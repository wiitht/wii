package org.wiitht.wii.core.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import org.wiitht.wii.core.internal.manage.ManageConfiguration;
import org.wiitht.wii.core.internal.message.GrpcHttp2HeadersDecoder;
import org.wiitht.wii.core.internal.message.GrpcHttp2Messages;
import io.grpc.InternalMetadata;
import io.grpc.InternalStatus;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.internal.GrpcUtil;
import io.grpc.internal.KeepAliveManager;
import io.grpc.internal.StatsTraceContext;
import io.grpc.netty.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.netty.util.AsciiString;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static io.grpc.internal.GrpcUtil.CONTENT_TYPE_KEY;
import static io.grpc.internal.GrpcUtil.SERVER_KEEPALIVE_TIME_NANOS_DISABLED;
import static io.netty.handler.codec.http2.Http2CodecUtil.getEmbeddedHttp2Exception;
/**
 * @Author wii
 * @Date 18-12-13-下午4:50
 * @Version 1.0
 */
public class ProxyH2ConnectionHandler extends Http2ConnectionHandler {

    private static final long GRACEFUL_SHUTDOWN_NO_TIMEOUT = -1;
    private boolean autoTuneFlowControlOn = false;
    private int initialConnectionWindow;
    private ChannelHandlerContext ctx;
    private static final long KEEPALIVE_PING = 0xDEADL;
    private final FlowControlPinger flowControlPing = new FlowControlPinger();
    private static final long BDP_MEASUREMENT_PING = 1234;
    private static int maxHeaderListSize = GrpcUtil.DEFAULT_MAX_HEADER_LIST_SIZE;
    private ManageConfiguration manageConfiguration;
    private Http2Connection.PropertyKey streamKey;
    private static final AsciiString HTTP_METHOD = AsciiString.of(GrpcUtil.HTTP_METHOD);
    private static final AsciiString CONTENT_TYPE_HEADER = AsciiString.of(CONTENT_TYPE_KEY.name());

    public static ProxyH2ConnectionHandler newHandler(ManageConfiguration manageConfiguration){
        Http2FrameLogger frameLogger = new Http2FrameLogger(LogLevel.DEBUG, ProxyH2ConnectionHandler.class);
        Http2Connection connection = new DefaultHttp2Connection(true);
        Http2HeadersDecoder headersDecoder = new GrpcHttp2HeadersDecoder.GrpcHttp2ServerHeadersDecoder(maxHeaderListSize);

       /* Http2FrameReader baseFrameReader = new Http2InboundFrameLogger(
                new TestProxyHttp2FrameReader(headersDecoder), frameLogger);
        Http2FrameWriter baseFrameWriter =
                new Http2OutboundFrameLogger(new DefaultHttp2FrameWriter(), frameLogger);
        WeightedFairQueueByteDistributor dist = new WeightedFairQueueByteDistributor(connection);
        dist.allocationQuantum(16 * 1024); // Make benchmarks fast again.
        DefaultHttp2RemoteFlowController controller =
                new DefaultHttp2RemoteFlowController(connection, dist);
        connection.remote().flowController(controller);
        final KeepAliveEnforcer keepAliveEnforcer = new KeepAliveEnforcer(
                false, TimeUnit.MINUTES.toNanos(5), TimeUnit.NANOSECONDS);
        // Create the local flow controller configured to auto-refill the connection window.
        connection.local().flowController(
                new DefaultHttp2LocalFlowController(connection, DEFAULT_WINDOW_UPDATE_RATIO, true));
        WriteMonitoringFrameWriter frameWriter = new WriteMonitoringFrameWriter(baseFrameWriter, keepAliveEnforcer);
        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder,
                baseFrameReader);*/

        Http2FrameWriter frameWriter = new Http2OutboundFrameLogger(new DefaultHttp2FrameWriter(), frameLogger);
        Http2FrameReader frameReader = new Http2InboundFrameLogger(new DefaultHttp2FrameReader(), frameLogger);
        DefaultHttp2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        DefaultHttp2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, frameReader);
        return new ProxyH2ConnectionHandler(decoder, encoder, GrpcHttp2Messages.getHttp2Settings(), manageConfiguration);
    }

    private ProxyH2ConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings,
                                     ManageConfiguration configuration) {
        super(decoder, encoder, initialSettings);
        decoder.frameListener(new ProxyH2FrameListener());
        this.manageConfiguration = configuration;
        streamKey = encoder.connection().newKey();
        // During a graceful shutdown, wait until all streams are closed.
        gracefulShutdownTimeoutMillis(GRACEFUL_SHUTDOWN_NO_TIMEOUT);

        // Extract the connection window from the settings if it was set.
        this.initialConnectionWindow = initialSettings.initialWindowSize() == null ? -1 :
                initialSettings.initialWindowSize();
    }

    private void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers)
            throws Http2Exception {
        try {
            // Remove the leading slash of the path and get the fully qualified method name
            CharSequence path = headers.path();

            if (path == null) {
                respondWithHttpError(ctx, streamId, 404, Status.Code.UNIMPLEMENTED,
                        "Expected path but is missing");
                return;
            }

            if (path.charAt(0) != '/') {
                respondWithHttpError(ctx, streamId, 404, Status.Code.UNIMPLEMENTED,
                        String.format("Expected path to start with /: %s", path));
                return;
            }

            // Verify that the Content-Type is correct in the request.
            CharSequence contentType = headers.get(CONTENT_TYPE_HEADER);
            if (contentType == null) {
                respondWithHttpError(
                        ctx, streamId, 415, Status.Code.INTERNAL, "Content-Type is missing from the request");
                return;
            }
            String contentTypeString = contentType.toString();
            if (!GrpcUtil.isGrpcContentType(contentTypeString)) {
                respondWithHttpError(ctx, streamId, 415, Status.Code.INTERNAL,
                        String.format("Content-Type '%s' is not supported", contentTypeString));
                return;
            }

            if (!HTTP_METHOD.equals(headers.method())) {
                respondWithHttpError(ctx, streamId, 405, Status.Code.INTERNAL,
                        String.format("Method '%s' is not supported", headers.method()));
                return;
            }
            Http2Stream http2Stream = requireHttp2Stream(streamId);
            ProxyH2Exchange exchange = ProxyH2Exchange.newExchange(manageConfiguration);
            http2Stream.setProperty(streamKey, exchange);
            exchange.handleHeader(ctx, streamId, headers);
        } catch (Exception e) {
            //logger.log(Level.WARNING, "Exception in onHeadersRead()", e);
            // Throw an exception that will get handled by onStreamError.
            throw newStreamException(streamId, e);
        }
    }

    private void onDataRead(int streamId, ByteBuf data, int padding, boolean endOfStream)
            throws Http2Exception {
        flowControlPing().onDataRead(data.readableBytes(), padding);
        try {
            ProxyH2Exchange stream = serverStream(requireHttp2Stream(streamId));
            stream.handleData(streamId, data, padding, endOfStream);
        } catch (Throwable e) {
            //logger.log(Level.WARNING, "Exception in onDataRead()", e);
            // Throw an exception that will get handled by onStreamError.
            throw newStreamException(streamId, e);
        }
    }

    private void onRstStreamRead(int streamId, long errorCode) throws Http2Exception {
        try {
            /*NettyServerStream.TransportState stream = serverStream(connection().stream(streamId));
            if (stream != null) {
                stream.transportReportStatus(
                        Status.CANCELLED.withDescription("RST_STREAM received for code " + errorCode));
            }*/
        } catch (Throwable e) {
            //logger.log(Level.WARNING, "Exception in onRstStreamRead()", e);
            // Throw an exception that will get handled by onStreamError.
            throw newStreamException(streamId, e);
        }
    }

    /**
     * Returns the given processed bytes back to inbound flow control.
     */
    void returnProcessedBytes(Http2Stream http2Stream, int bytes) {
        try {
            decoder().flowController().consumeBytes(http2Stream, bytes);
        } catch (Http2Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void respondWithHttpError(
            ChannelHandlerContext ctx, int streamId, int code, Status.Code statusCode, String msg) {
        Metadata metadata = new Metadata();
        metadata.put(InternalStatus.CODE_KEY, statusCode.toStatus());
        metadata.put(InternalStatus.MESSAGE_KEY, msg);
        byte[][] serialized = InternalMetadata.serialize(metadata);

        Http2Headers headers = new DefaultHttp2Headers(true, serialized.length / 2)
                .status("" + code)
                .set(CONTENT_TYPE_HEADER, "text/plain; encoding=utf-8");
        for (int i = 0; i < serialized.length; i += 2) {
            headers.add(new AsciiString(serialized[i], false), new AsciiString(serialized[i + 1], false));
        }
        encoder().writeHeaders(ctx, streamId, headers, 0, false, ctx.newPromise());
        ByteBuf msgBuf = ByteBufUtil.writeUtf8(ctx.alloc(), msg);
        encoder().writeData(ctx, streamId, msgBuf, 0, true, ctx.newPromise());
    }

    private Http2Stream requireHttp2Stream(int streamId) {
        Http2Stream stream = connection().stream(streamId);
        if (stream == null) {
            // This should never happen.
            throw new AssertionError("Stream does not exist: " + streamId);
        }
        return stream;
    }

    /**
     * Returns the server stream associated to the given HTTP/2 stream object.
     */
    private ProxyH2Exchange serverStream(Http2Stream stream) {
        return stream == null ? null : (ProxyH2Exchange) stream.getProperty(streamKey);
    }

    private Http2Exception newStreamException(int streamId, Throwable cause) {
        return Http2Exception.streamError(
                streamId, Http2Error.INTERNAL_ERROR, cause, Strings.nullToEmpty(cause.getMessage()));
    }

    /**
     * receive 数据
     * @param ctx
     * @param msg
     * @param promise
     * @throws Exception
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        /*if (msg instanceof SendDataCommand) {
            SendDataCommand command = (SendDataCommand)msg;
            ctx.write(command.content());
        } else {
            ctx.write(msg, promise);
        }*/
    }

    private final class KeepAlivePinger implements KeepAliveManager.KeepAlivePinger {
        final ChannelHandlerContext ctx;

        KeepAlivePinger(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void ping() {
            ChannelFuture pingFuture = encoder().writePing(
                    ctx, false , KEEPALIVE_PING, ctx.newPromise());
            ctx.flush();
           /* if (transportTracer != null) {
                pingFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            transportTracer.reportKeepAliveSent();
                        }
                    }
                });
            }*/
        }

        @Override
        public void onPingTimeout() {

        }

        /*@Override
        public void onPingTimeout() {
            try {
                forcefulClose(
                        ctx,
                        new ForcefulCloseCommand(Status.UNAVAILABLE
                                .withDescription("Keepalive failed. The connection is likely gone")),
                        ctx.newPromise());
            } catch (Exception ex) {
                try {
                    exceptionCaught(ctx, ex);
                } catch (Exception ex2) {
                    logger.log(Level.WARNING, "Exception while propagating exception", ex2);
                    logger.log(Level.WARNING, "Original failure", ex);
                }
            }
        }*/
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (GrpcUtil.DEFAULT_SERVER_KEEPALIVE_TIME_NANOS != SERVER_KEEPALIVE_TIME_NANOS_DISABLED) {
            KeepAliveManager keepAliveManager = new KeepAliveManager(new KeepAlivePinger(ctx), ctx.executor(),
                    GrpcUtil.DEFAULT_SERVER_KEEPALIVE_TIME_NANOS, GrpcUtil.DEFAULT_SERVER_KEEPALIVE_TIME_NANOS, true /* keepAliveDuringTransportIdle */);
            keepAliveManager.onTransportStarted();
        }

        this.ctx = ctx;
        // Sends the connection preface if we haven't already.
        super.handlerAdded(ctx);
        sendInitialConnectionWindow();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Sends connection preface if we haven't already.
        super.channelActive(ctx);
        sendInitialConnectionWindow();
    }

    @Override
    public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Http2Exception embedded = getEmbeddedHttp2Exception(cause);
        if (embedded == null) {
            // There was no embedded Http2Exception, assume it's a connection error. Subclasses are
            // responsible for storing the appropriate status and shutting down the connection.
            onError(ctx, /* outbound= */ false, cause);
        } else {
            super.exceptionCaught(ctx, cause);
        }
    }

    protected final ChannelHandlerContext ctx() {
        return ctx;
    }

    /**
     * Sends initial connection window to the remote endpoint if necessary.
     */
    private void sendInitialConnectionWindow() throws Http2Exception {
        if (ctx.channel().isActive() && initialConnectionWindow > 0) {
            Http2Stream connectionStream = connection().connectionStream();
            int currentSize = connection().local().flowController().windowSize(connectionStream);
            int delta = initialConnectionWindow - currentSize;
            decoder().flowController().incrementWindowSize(connectionStream, delta);
            initialConnectionWindow = -1;
            ctx.flush();
        }
    }

    @VisibleForTesting
    ProxyH2ConnectionHandler.FlowControlPinger flowControlPing() {
        return flowControlPing;
    }

    @VisibleForTesting
    void setAutoTuneFlowControl(boolean isOn) {
        autoTuneFlowControlOn = isOn;
    }

    /**
     * Class for handling flow control pinging and flow control window updates as necessary.
     */
    final class FlowControlPinger {

        private static final int MAX_WINDOW_SIZE = 8 * 1024 * 1024;
        private int pingCount;
        private int pingReturn;
        private boolean pinging;
        private int dataSizeSincePing;
        private float lastBandwidth; // bytes per second
        private long lastPingTime;

        public long payload() {
            return BDP_MEASUREMENT_PING;
        }

        public int maxWindow() {
            return MAX_WINDOW_SIZE;
        }

        public void onDataRead(int dataLength, int paddingLength) {
            if (!autoTuneFlowControlOn) {
                return;
            }
            if (!isPinging()) {
                setPinging(true);
                sendPing(ctx());
            }
            incrementDataSincePing(dataLength + paddingLength);
        }

        public void updateWindow() throws Http2Exception {
            if (!autoTuneFlowControlOn) {
                return;
            }
            pingReturn++;
            long elapsedTime = (System.nanoTime() - lastPingTime);
            if (elapsedTime == 0) {
                elapsedTime = 1;
            }
            long bandwidth = (getDataSincePing() * TimeUnit.SECONDS.toNanos(1)) / elapsedTime;
            Http2LocalFlowController fc = decoder().flowController();
            // Calculate new window size by doubling the observed BDP, but cap at max window
            int targetWindow = Math.min(getDataSincePing() * 2, MAX_WINDOW_SIZE);
            setPinging(false);
            int currentWindow = fc.initialWindowSize(connection().connectionStream());
            if (targetWindow > currentWindow && bandwidth > lastBandwidth) {
                lastBandwidth = bandwidth;
                int increase = targetWindow - currentWindow;
                fc.incrementWindowSize(connection().connectionStream(), increase);
                fc.initialWindowSize(targetWindow);
                Http2Settings settings = new Http2Settings();
                settings.initialWindowSize(targetWindow);
                frameWriter().writeSettings(ctx(), settings, ctx().newPromise());
            }

        }

        private boolean isPinging() {
            return pinging;
        }

        private void setPinging(boolean pingOut) {
            pinging = pingOut;
        }

        private void sendPing(ChannelHandlerContext ctx) {
            setDataSizeSincePing(0);
            lastPingTime = System.nanoTime();
            encoder().writePing(ctx, false, BDP_MEASUREMENT_PING, ctx.newPromise());
            pingCount++;
        }

        private void incrementDataSincePing(int increase) {
            int currentSize = getDataSincePing();
            setDataSizeSincePing(currentSize + increase);
        }

        @VisibleForTesting
        int getPingCount() {
            return pingCount;
        }

        @VisibleForTesting
        int getPingReturn() {
            return pingReturn;
        }

        @VisibleForTesting
        int getDataSincePing() {
            return dataSizeSincePing;
        }

        @VisibleForTesting
        void setDataSizeSincePing(int dataSize) {
            dataSizeSincePing = dataSize;
        }
    }

    final class ProxyH2FrameListener extends Http2FrameAdapter {

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
           /* if (firstSettings) {
                firstSettings = false;
                // Delay transportReady until we see the client's HTTP handshake, for coverage with
                // handshakeTimeout
                attributes = transportListener.transportReady(negotiationAttributes);
            }*/
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                              boolean endOfStream) throws Http2Exception {
            ProxyH2ConnectionHandler.this.onDataRead(streamId, data, padding, endOfStream);
            return padding;
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx,
                                  int streamId,
                                  Http2Headers headers,
                                  int streamDependency,
                                  short weight,
                                  boolean exclusive,
                                  int padding,
                                  boolean endStream) throws Http2Exception {
           /* if (keepAliveManager != null) {
                keepAliveManager.onDataReceived();
            }*/
            ProxyH2ConnectionHandler.this.onHeadersRead(ctx, streamId, headers);
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                throws Http2Exception {
           /* if (keepAliveManager != null) {
                keepAliveManager.onDataReceived();
            }
            NettyProxyHandler.this.onRstStreamRead(streamId, errorCode);*/
        }

        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {

        }

        public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {

        }
    }

}