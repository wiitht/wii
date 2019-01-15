package org.wiitht.wii.eel.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.grpc.*;
import io.grpc.internal.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.netty.util.AsciiString;
import org.wiitht.wii.eel.trail.GrpcProxyClient;
import org.wiitht.wii.eel.trail.SendFrameCommand;
import org.wiitht.wii.eel.trail.SendHeaderCommand;
import org.wiitht.wii.eel.trail.StreamIdHolder;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.netty.handler.codec.http2.DefaultHttp2LocalFlowController.DEFAULT_WINDOW_UPDATE_RATIO;

/**
 * @Author tanghong
 * @Date 18-8-15-下午4:07
 * @Version 1.0
 */
public class NettyProxyHandler extends AbstractNettyProxyHandler {
    private static final Logger logger = Logger.getLogger(NettyProxyHandler.class.getName());
    private static final long KEEPALIVE_PING = 0xDEADL;
    private static final long GRACEFUL_SHUTDOWN_PING = 0x97ACEF001L;
    private static final long GRACEFUL_SHUTDOWN_PING_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final Http2Connection.PropertyKey streamKey;
    private final ServerTransportListener transportListener;
    private final int maxMessageSize;
    private final long keepAliveTimeInNanos;
    private final long keepAliveTimeoutInNanos;
    private final long maxConnectionAgeInNanos;
    private final long maxConnectionAgeGraceInNanos;
    private final List<ServerStreamTracer.Factory> streamTracerFactories;
    private final TransportTracer transportTracer;
    private final KeepAliveEnforcer keepAliveEnforcer;
    /** Incomplete attributes produced by negotiator. */
    private Attributes negotiationAttributes;
    private Channelz.Security securityInfo;
    /** Completed attributes produced by transportReady. */
    private Attributes attributes;
    private Throwable connectionError;
    private boolean teWarningLogged;
    private WriteQueue serverWriteQueue;
    private AsciiString lastKnownAuthority;
    @CheckForNull
    private KeepAliveManager keepAliveManager;
    //@CheckForNull
    //private MaxConnectionIdleManager maxConnectionIdleManager;
    @CheckForNull
    private ScheduledFuture<?> maxConnectionAgeMonitor;
    @CheckForNull
    private NettyProxyHandler.GracefulShutdown gracefulShutdown;

    static final long MAX_CONNECTION_AGE_NANOS_DISABLED = Long.MAX_VALUE;

    public static NettyProxyHandler newHandler(
            ServerTransportListener transportListener,
            ChannelPromise channelUnused,
            List<ServerStreamTracer.Factory> streamTracerFactories,
            TransportTracer transportTracer,
            int maxStreams,
            int flowControlWindow,
            int maxHeaderListSize,
            int maxMessageSize,
            long keepAliveTimeInNanos,
            long keepAliveTimeoutInNanos,
            long maxConnectionIdleInNanos,
            long maxConnectionAgeInNanos,
            long maxConnectionAgeGraceInNanos,
            boolean permitKeepAliveWithoutCalls,
            long permitKeepAliveTimeInNanos) {

        Preconditions.checkArgument(maxHeaderListSize > 0, "maxHeaderListSize must be positive");

        Http2FrameLogger frameLogger = new Http2FrameLogger(LogLevel.DEBUG, NettyProxyHandler.class);

        Http2HeadersDecoder headersDecoder = new GrpcProxyHttp2HeadersUtils.GrpcHttp2ServerHeadersDecoder(maxHeaderListSize);

        Http2FrameReader frameReader = new Http2InboundFrameLogger(new DefaultHttp2FrameReader(headersDecoder), frameLogger);

        Http2FrameWriter frameWriter = new Http2OutboundFrameLogger(new DefaultHttp2FrameWriter(), frameLogger);


        return newHandler(
                channelUnused,
                frameReader,
                frameWriter,
                transportListener,
                streamTracerFactories,
                transportTracer,
                maxStreams,
                flowControlWindow,
                maxHeaderListSize,
                maxMessageSize,
                keepAliveTimeInNanos,
                keepAliveTimeoutInNanos,
                maxConnectionIdleInNanos,
                maxConnectionAgeInNanos,
                maxConnectionAgeGraceInNanos,
                permitKeepAliveWithoutCalls,
                permitKeepAliveTimeInNanos);
    }

    @VisibleForTesting
    static NettyProxyHandler newHandler(
            ChannelPromise channelUnused,
            Http2FrameReader frameReader,
            Http2FrameWriter frameWriter,
            ServerTransportListener transportListener,
            List<ServerStreamTracer.Factory> streamTracerFactories,
            TransportTracer transportTracer,
            int maxStreams,
            int flowControlWindow,
            int maxHeaderListSize,
            int maxMessageSize,
            long keepAliveTimeInNanos,
            long keepAliveTimeoutInNanos,
            long maxConnectionIdleInNanos,
            long maxConnectionAgeInNanos,
            long maxConnectionAgeGraceInNanos,
            boolean permitKeepAliveWithoutCalls,
            long permitKeepAliveTimeInNanos) {
        Preconditions.checkArgument(maxStreams > 0, "maxStreams must be positive");
        Preconditions.checkArgument(flowControlWindow > 0, "flowControlWindow must be positive");
        Preconditions.checkArgument(maxHeaderListSize > 0, "maxHeaderListSize must be positive");
        Preconditions.checkArgument(maxMessageSize > 0, "maxMessageSize must be positive");

        final Http2Connection connection = new DefaultHttp2Connection(true);
        WeightedFairQueueByteDistributor dist = new WeightedFairQueueByteDistributor(connection);
        dist.allocationQuantum(16 * 1024); // Make benchmarks fast again.
        DefaultHttp2RemoteFlowController controller =
                new DefaultHttp2RemoteFlowController(connection, dist);
        connection.remote().flowController(controller);
        final KeepAliveEnforcer keepAliveEnforcer = new KeepAliveEnforcer(
                permitKeepAliveWithoutCalls, permitKeepAliveTimeInNanos, TimeUnit.NANOSECONDS);

        // Create the local flow controller configured to auto-refill the connection window.
        connection.local().flowController(
                new DefaultHttp2LocalFlowController(connection, DEFAULT_WINDOW_UPDATE_RATIO, true));
        frameWriter = new NettyProxyHandler.WriteMonitoringFrameWriter(frameWriter, keepAliveEnforcer);
        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder,
                frameReader);

        Http2Settings settings = new Http2Settings();
        settings.initialWindowSize(flowControlWindow);
        settings.maxConcurrentStreams(maxStreams);
        settings.maxHeaderListSize(maxHeaderListSize);

        return new NettyProxyHandler(
                channelUnused,
                connection,
                transportListener,
                streamTracerFactories,
                transportTracer,
                decoder, encoder, settings,
                maxMessageSize,
                keepAliveTimeInNanos, keepAliveTimeoutInNanos,
                maxConnectionIdleInNanos,
                maxConnectionAgeInNanos, maxConnectionAgeGraceInNanos,
                keepAliveEnforcer);
    }

    private NettyProxyHandler(
            ChannelPromise channelUnused,
            final Http2Connection connection,
            ServerTransportListener transportListener,
            List<ServerStreamTracer.Factory> streamTracerFactories,
            TransportTracer transportTracer,
            Http2ConnectionDecoder decoder,
            Http2ConnectionEncoder encoder,
            Http2Settings settings,
            int maxMessageSize,
            long keepAliveTimeInNanos,
            long keepAliveTimeoutInNanos,
            long maxConnectionIdleInNanos,
            long maxConnectionAgeInNanos,
            long maxConnectionAgeGraceInNanos,
            final KeepAliveEnforcer keepAliveEnforcer) {
        super(channelUnused, decoder, encoder, settings);

        /*final MaxConnectionIdleManager maxConnectionIdleManager;
        if (maxConnectionIdleInNanos == MAX_CONNECTION_IDLE_NANOS_DISABLED) {
            maxConnectionIdleManager = null;
        } else {
            maxConnectionIdleManager = new MaxConnectionIdleManager(maxConnectionIdleInNanos) {
                @Override
                void close(ChannelHandlerContext ctx) {
                    if (gracefulShutdown == null) {
                        gracefulShutdown = new NettyProxyHandler.GracefulShutdown("max_idle", null);
                        gracefulShutdown.start(ctx);
                        ctx.flush();
                    }
                }
            };
        }

        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void onStreamActive(Http2Stream stream) {
                if (connection.numActiveStreams() == 1) {
                    keepAliveEnforcer.onTransportActive();
                    if (maxConnectionIdleManager != null) {
                        maxConnectionIdleManager.onTransportActive();
                    }
                }
            }

            @Override
            public void onStreamClosed(Http2Stream stream) {
                if (connection.numActiveStreams() == 0) {
                    keepAliveEnforcer.onTransportIdle();
                    if (maxConnectionIdleManager != null) {
                        maxConnectionIdleManager.onTransportIdle();
                    }
                }
            }
        });*/

        //checkArgument(maxMessageSize >= 0, "maxMessageSize must be >= 0");
        this.maxMessageSize = maxMessageSize;
        this.keepAliveTimeInNanos = keepAliveTimeInNanos;
        this.keepAliveTimeoutInNanos = keepAliveTimeoutInNanos;
        this.maxConnectionAgeInNanos = maxConnectionAgeInNanos;
        this.maxConnectionAgeGraceInNanos = maxConnectionAgeGraceInNanos;
        this.keepAliveEnforcer = null; //checkNotNull(keepAliveEnforcer, "keepAliveEnforcer");

        streamKey = encoder.connection().newKey();
        this.transportListener = null; //checkNotNull(transportListener, "transportListener");
        this.streamTracerFactories = null;
        //checkNotNull(streamTracerFactories, "streamTracerFactories");
        this.transportTracer = null;
        //// checkNotNull(transportTracer, "transportTracer");

        // Set the frame listener on the decoder.
        decoder().frameListener(new NettyProxyHandler.FrameListener());
    }

    @Nullable
    Throwable connectionError() {
        return connectionError;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        serverWriteQueue = new WriteQueue(ctx.channel());
/*
        // init max connection age monitor
        if (maxConnectionAgeInNanos != MAX_CONNECTION_AGE_NANOS_DISABLED) {
            maxConnectionAgeMonitor = ctx.executor().schedule(
                    new LogExceptionRunnable(new Runnable() {
                        @Override
                        public void run() {
                            if (gracefulShutdown == null) {
                                gracefulShutdown = new NettyProxyHandler.GracefulShutdown("max_age", maxConnectionAgeGraceInNanos);
                                gracefulShutdown.start(ctx);
                                ctx.flush();
                            }
                        }
                    }),
                    maxConnectionAgeInNanos,
                    TimeUnit.NANOSECONDS);
        }

        if (maxConnectionIdleManager != null) {
            maxConnectionIdleManager.start(ctx);
        }

        if (keepAliveTimeInNanos != SERVER_KEEPALIVE_TIME_NANOS_DISABLED) {
            keepAliveManager = new KeepAliveManager(new NettyProxyHandler.KeepAlivePinger(ctx), ctx.executor(),
                    keepAliveTimeInNanos, keepAliveTimeoutInNanos, true  keepAliveDuringTransportIdle );
            keepAliveManager.onTransportStarted();
        }


        if (transportTracer != null) {
            assert encoder().connection().equals(decoder().connection());
            final Http2Connection connection = encoder().connection();
            transportTracer.setFlowControlWindowReader(new TransportTracer.FlowControlReader() {
                private final Http2FlowController local = connection.local().flowController();
                private final Http2FlowController remote = connection.remote().flowController();

                @Override
                public TransportTracer.FlowControlWindows read() {
                    assert ctx.executor().inEventLoop();
                    return new TransportTracer.FlowControlWindows(
                            local.windowSize(connection.connectionStream()),
                            remote.windowSize(connection.connectionStream()));
                }
            });
        }*/
        super.handlerAdded(ctx);
    }

    private void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers)
            throws Http2Exception {
        GrpcProxyClient.start(getWriteQueue());
        DefaultHttp2Headers http2Headers = new DefaultHttp2Headers();
        http2Headers.authority(new AsciiString("localhost:50051"));
        http2Headers.method(headers.method());
        http2Headers.path(headers.path());
        http2Headers.scheme(headers.scheme());
        http2Headers.set(Utils.CONTENT_TYPE_HEADER, headers.get(Utils.CONTENT_TYPE_HEADER));
        http2Headers.set(Utils.TE_HEADER, Utils.TE_TRAILERS);
        http2Headers.set(Utils.USER_AGENT, new AsciiString("grpc-java-netty/1.14.0"));
        http2Headers.set(new AsciiString(GrpcUtil.MESSAGE_ACCEPT_ENCODING), new AsciiString("gzip"));
        SendHeaderCommand command = SendHeaderCommand.createHeaders(new StreamIdHolder() {
            @Override
            public int id() {
                return streamId;
            }
        }, http2Headers);
        GrpcProxyClient.sendHeader(command);
    }

    private void onDataRead(int streamId, ByteBuf data, int padding, boolean endOfStream)
            throws Http2Exception {
        //flowControlPing().onDataRead(data.readableBytes(), padding);
        SendFrameCommand frameCommand = new SendFrameCommand(new StreamIdHolder() {
            @Override
            public int id() {
                return streamId;
            }
        }, data, endOfStream);
        GrpcProxyClient.sendFrame(frameCommand);
       /* try {
            NettyProxyServerStream.TransportState stream = serverStream(requireHttp2Stream(streamId));
            stream.inboundDataReceived(data, endOfStream);
        } catch (Throwable e) {
            logger.logging(Level.WARNING, "Exception in onDataRead()", e);
            // Throw an exception that will get handled by onStreamError.
            throw newStreamException(streamId, e);
        }*/
    }

    private void onRstStreamRead(int streamId, long errorCode) throws Http2Exception {
        try {
            NettyProxyServerStream.TransportState stream = serverStream(connection().stream(streamId));
            if (stream != null) {
                stream.transportReportStatus(
                        Status.CANCELLED.withDescription("RST_STREAM received for code " + errorCode));
            }
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Exception in onRstStreamRead()", e);
            // Throw an exception that will get handled by onStreamError.
            throw newStreamException(streamId, e);
        }
    }

    @Override
    protected void onConnectionError(ChannelHandlerContext ctx, boolean outbound, Throwable cause,
                                     Http2Exception http2Ex) {
        logger.log(Level.FINE, "Connection Error", cause);
        connectionError = cause;
        super.onConnectionError(ctx, outbound, cause, http2Ex);
    }

    @Override
    protected void onStreamError(ChannelHandlerContext ctx, boolean outbound, Throwable cause,
                                 Http2Exception.StreamException http2Ex) {
        logger.log(Level.WARNING, "Stream Error", cause);
        NettyProxyServerStream.TransportState serverStream = serverStream(
                connection().stream(Http2Exception.streamId(http2Ex)));
        if (serverStream != null) {
            serverStream.transportReportStatus(Utils.statusFromThrowable(cause));
        }
        // TODO(ejona): Abort the stream by sending headers to help the client with debugging.
        // Delegate to the base class to send a RST_STREAM.
        super.onStreamError(ctx, outbound, cause, http2Ex);
    }

    @Override
    public void handleProtocolNegotiationCompleted(
            Attributes attrs, Channelz.Security securityInfo) {
        negotiationAttributes = attrs;
        this.securityInfo = securityInfo;
    }

    Channelz.Security getSecurityInfo() {
        return securityInfo;
    }

    @VisibleForTesting
    KeepAliveManager getKeepAliveManagerForTest() {
        return keepAliveManager;
    }

    @VisibleForTesting
    void setKeepAliveManagerForTest(KeepAliveManager keepAliveManager) {
        this.keepAliveManager = keepAliveManager;
    }

    /**
     * Handler for the Channel shutting down.
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            if (keepAliveManager != null) {
                keepAliveManager.onTransportTermination();
            }
          /*  if (maxConnectionIdleManager != null) {
                maxConnectionIdleManager.onTransportTermination();
            }*/
            if (maxConnectionAgeMonitor != null) {
                maxConnectionAgeMonitor.cancel(false);
            }
            final Status status =
                    Status.UNAVAILABLE.withDescription("connection terminated for unknown reason");
            // Any streams that are still active must be closed
            connection().forEachActiveStream(new Http2StreamVisitor() {
                @Override
                public boolean visit(Http2Stream stream) throws Http2Exception {
                    NettyProxyServerStream.TransportState serverStream = serverStream(stream);
                    if (serverStream != null) {
                        serverStream.transportReportStatus(status);
                    }
                    return true;
                }
            });
        } finally {
            super.channelInactive(ctx);
        }
    }


    WriteQueue getWriteQueue() {
        return serverWriteQueue;
    }

    /**
     * Handler for commands sent from the stream.
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
            throws Exception {
        if (msg instanceof SendProxyFrameCommand) {
            sendGrpcFrame(ctx, (SendProxyFrameCommand) msg, promise);
        } else if (msg instanceof SendProxyHeaderCommand) {
            sendHeader(ctx, (SendProxyHeaderCommand) msg, promise);
        } else if (msg instanceof SendDataCommand) {
            SendDataCommand command = (SendDataCommand)msg;
            ctx.write(command.content());
        } else {
            AssertionError e =
                    new AssertionError("Write called for unexpected type: " + msg.getClass().getName());
            //ReferenceCountUtil.release(msg);
            promise.setFailure(e);
            throw e;
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

    private void closeStreamWhenDone(ChannelPromise promise, int streamId) throws Http2Exception {
        final NettyProxyServerStream.TransportState stream = serverStream(requireHttp2Stream(streamId));
        promise.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                stream.complete();
            }
        });
    }

    /**
     * Sends the given gRPC frame to the client.
     */
    private void sendGrpcFrame(ChannelHandlerContext ctx, SendProxyFrameCommand cmd,
                               ChannelPromise promise) throws Http2Exception {
        /*if (cmd.endStream()) {
            closeStreamWhenDone(promise, cmd.streamId());
        }*/
        encoder().writeData(ctx, cmd.streamId(), cmd.content(), 0, false, promise);
    }

    private void sendHeader(ChannelHandlerContext ctx, SendProxyHeaderCommand cmd,
                               ChannelPromise promise) throws Http2Exception {
        Http2Headers headers = cmd.headers();
        DefaultHttp2Headers http2Headers = new DefaultHttp2Headers();
        http2Headers.set(Utils.CONTENT_TYPE_HEADER, headers.get(Utils.CONTENT_TYPE_HEADER));
        http2Headers.set(new AsciiString(GrpcUtil.MESSAGE_ENCODING), new AsciiString("identity"));
        http2Headers.set(new AsciiString(":status"), headers.status());
        http2Headers.set(new AsciiString(GrpcUtil.MESSAGE_ACCEPT_ENCODING), new AsciiString("gzip"));
        encoder().writeHeaders(ctx, cmd.stream().id(), http2Headers, 0, false, promise);
    }
    /**
     * Sends the response headers to the client.
     */
   /* private void sendResponseHeaders(ChannelHandlerContext ctx, SendResponseHeadersCommand cmd,
                                     ChannelPromise promise) throws Http2Exception {
        // TODO(carl-mastrangelo): remove this check once https://github.com/netty/netty/issues/6296 is
        // fixed.
        int streamId = cmd.stream().id();
        Http2Stream stream = connection().stream(streamId);
        if (stream == null) {
            resetStream(ctx, streamId, Http2Error.CANCEL.code(), promise);
            return;
        }
        if (cmd.endOfStream()) {
            closeStreamWhenDone(promise, streamId);
        }
        encoder().writeHeaders(ctx, streamId, cmd.headers(), 0, cmd.endOfStream(), promise);
    }*/

   /* private void cancelStream(ChannelHandlerContext ctx, CancelServerStreamCommand cmd,
                              ChannelPromise promise) {
        // Notify the listener if we haven't already.
        cmd.stream().transportReportStatus(cmd.reason());
        // Terminate the stream.
        encoder().writeRstStream(ctx, cmd.stream().id(), Http2Error.CANCEL.code(), promise);
    }
*/
  /*  private void forcefulClose(final ChannelHandlerContext ctx, final ForcefulCloseCommand msg,
                               ChannelPromise promise) throws Exception {
        close(ctx, promise);
        connection().forEachActiveStream(new Http2StreamVisitor() {
            @Override
            public boolean visit(Http2Stream stream) throws Http2Exception {
                NettyProxyServerStream.TransportState serverStream = serverStream(stream);
                if (serverStream != null) {
                    serverStream.transportReportStatus(msg.getStatus());
                    resetStream(ctx, stream.id(), Http2Error.CANCEL.code(), ctx.newPromise());
                }
                stream.close();
                return true;
            }
        });
    }*/

    private void respondWithHttpError(
            ChannelHandlerContext ctx, int streamId, int code, Status.Code statusCode, String msg) {
        Metadata metadata = new Metadata();
        metadata.put(InternalStatus.CODE_KEY, statusCode.toStatus());
        metadata.put(InternalStatus.MESSAGE_KEY, msg);
        byte[][] serialized = InternalMetadata.serialize(metadata);

        Http2Headers headers = new DefaultHttp2Headers(true, serialized.length / 2)
                .status("" + code)
                .set(Utils.CONTENT_TYPE_HEADER, "text/plain; encoding=utf-8");
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
    private NettyProxyServerStream.TransportState serverStream(Http2Stream stream) {
        return stream == null ? null : (NettyProxyServerStream.TransportState) stream.getProperty(streamKey);
    }

    private Http2Exception newStreamException(int streamId, Throwable cause) {
        return Http2Exception.streamError(
                streamId, Http2Error.INTERNAL_ERROR, cause, Strings.nullToEmpty(cause.getMessage()));
    }

    private class FrameListener extends Http2FrameAdapter {
        private boolean firstSettings = true;

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
            /*if (keepAliveManager != null) {
                keepAliveManager.onDataReceived();
            }*/
            NettyProxyHandler.this.onDataRead(streamId, data, padding, endOfStream);
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
            NettyProxyHandler.this.onHeadersRead(ctx, streamId, headers);
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                throws Http2Exception {
            if (keepAliveManager != null) {
                keepAliveManager.onDataReceived();
            }
            NettyProxyHandler.this.onRstStreamRead(streamId, errorCode);
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
            if (keepAliveManager != null) {
                keepAliveManager.onDataReceived();
            }
          /*  if (!keepAliveEnforcer.pingAcceptable()) {
                ByteBuf debugData = ByteBufUtil.writeAscii(ctx.alloc(), "too_many_pings");
                goAway(ctx, connection().remote().lastStreamCreated(), Http2Error.ENHANCE_YOUR_CALM.code(),
                        debugData, ctx.newPromise());
                Status status = Status.RESOURCE_EXHAUSTED.withDescription("Too many pings from client");
                try {
                    forcefulClose(ctx, new ForcefulCloseCommand(status), ctx.newPromise());
                } catch (Exception ex) {
                    onError(ctx, *//* outbound= *//* true, ex);
                }
            }*/
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
            if (keepAliveManager != null) {
                keepAliveManager.onDataReceived();
            }
            if (data == flowControlPing().payload()) {
                flowControlPing().updateWindow();
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, String.format("Window: %d",
                            decoder().flowController().initialWindowSize(connection().connectionStream())));
                }
            } else if (data == GRACEFUL_SHUTDOWN_PING) {
                if (gracefulShutdown == null) {
                    // this should never happen
                    logger.warning("Received GRACEFUL_SHUTDOWN_PING Ack but gracefulShutdown is null");
                } else {
                    gracefulShutdown.secondGoAwayAndClose(ctx);
                }
            } else if (data != KEEPALIVE_PING) {
                logger.warning("Received unexpected ping ack. No ping outstanding");
            }
        }
    }

    private final class KeepAlivePinger implements KeepAliveManager.KeepAlivePinger {
        final ChannelHandlerContext ctx;

        KeepAlivePinger(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void ping() {
            ChannelFuture pingFuture = encoder().writePing(
                    ctx, false /* isAck */, KEEPALIVE_PING, ctx.newPromise());
            ctx.flush();
            if (transportTracer != null) {
                pingFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            transportTracer.reportKeepAliveSent();
                        }
                    }
                });
            }
        }

        @Override
        public void onPingTimeout() {
           /* try {
                forcefulClose(
                        ctx,
                        new ForcefulCloseCommand(Status.UNAVAILABLE
                                .withDescription("Keepalive failed. The connection is likely gone")),
                        ctx.newPromise());
            } catch (Exception ex) {
                try {
                    exceptionCaught(ctx, ex);
                } catch (Exception ex2) {
                    logger.logging(Level.WARNING, "Exception while propagating exception", ex2);
                    logger.logging(Level.WARNING, "Original failure", ex);
                }
            }*/
        }
    }

    private final class GracefulShutdown {
        String goAwayMessage;

        /**
         * The grace time between starting graceful shutdown and closing the netty channel,
         * {@code null} is unspecified.
         */
        @CheckForNull
        Long graceTimeInNanos;

        /**
         * True if ping is Acked or ping is timeout.
         */
        boolean pingAckedOrTimeout;

        Future<?> pingFuture;

        GracefulShutdown(String goAwayMessage,
                         @Nullable Long graceTimeInNanos) {
            this.goAwayMessage = goAwayMessage;
            this.graceTimeInNanos = graceTimeInNanos;
        }

        /**
         * Sends out first GOAWAY and ping, and schedules second GOAWAY and close.
         */
        void start(final ChannelHandlerContext ctx) {
            goAway(
                    ctx,
                    Integer.MAX_VALUE,
                    Http2Error.NO_ERROR.code(),
                    ByteBufUtil.writeAscii(ctx.alloc(), goAwayMessage),
                    ctx.newPromise());

            long gracefulShutdownPingTimeout = GRACEFUL_SHUTDOWN_PING_TIMEOUT_NANOS;
            pingFuture = ctx.executor().schedule(
                    new Runnable() {
                        @Override
                        public void run() {
                            secondGoAwayAndClose(ctx);
                        }
                    },
                    GRACEFUL_SHUTDOWN_PING_TIMEOUT_NANOS,
                    TimeUnit.NANOSECONDS);

            encoder().writePing(ctx, false /* isAck */, GRACEFUL_SHUTDOWN_PING, ctx.newPromise());
        }

        void secondGoAwayAndClose(ChannelHandlerContext ctx) {
            if (pingAckedOrTimeout) {
                return;
            }
            pingAckedOrTimeout = true;

            checkNotNull(pingFuture, "pingFuture");
            pingFuture.cancel(false);

            // send the second GOAWAY with last stream id
            goAway(
                    ctx,
                    connection().remote().lastStreamCreated(),
                    Http2Error.NO_ERROR.code(),
                    ByteBufUtil.writeAscii(ctx.alloc(), goAwayMessage),
                    ctx.newPromise());

            // gracefully shutdown with specified grace time
            long savedGracefulShutdownTimeMillis = gracefulShutdownTimeoutMillis();
            long gracefulShutdownTimeoutMillis = savedGracefulShutdownTimeMillis;
            if (graceTimeInNanos != null) {
                gracefulShutdownTimeoutMillis = TimeUnit.NANOSECONDS.toMillis(graceTimeInNanos);
            }
            try {
                gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis);
                close(ctx, ctx.newPromise());
            } catch (Exception e) {
                onError(ctx, /* outbound= */ true, e);
            } finally {
                gracefulShutdownTimeoutMillis(savedGracefulShutdownTimeMillis);
            }
        }
    }

    // Use a frame writer so that we know when frames are through flow control and actually being
    // written.
    private static class WriteMonitoringFrameWriter extends DecoratingHttp2FrameWriter {
        private final KeepAliveEnforcer keepAliveEnforcer;

        public WriteMonitoringFrameWriter(Http2FrameWriter delegate,
                                          KeepAliveEnforcer keepAliveEnforcer) {
            super(delegate);
            this.keepAliveEnforcer = keepAliveEnforcer;
        }

        @Override
        public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                       int padding, boolean endStream, ChannelPromise promise) {
            keepAliveEnforcer.resetCounters();
            return super.writeData(ctx, streamId, data, padding, endStream, promise);
        }

        @Override
        public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                          int padding, boolean endStream, ChannelPromise promise) {
            keepAliveEnforcer.resetCounters();
            return super.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
        }

        @Override
        public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                          int streamDependency, short weight, boolean exclusive, int padding, boolean endStream,
                                          ChannelPromise promise) {
            keepAliveEnforcer.resetCounters();
            return super.writeHeaders(ctx, streamId, headers, streamDependency, weight, exclusive,
                    padding, endStream, promise);
        }
    }
}
