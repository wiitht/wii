package org.wiitht.wii.dex.proxy;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.netty.GrpcHttp2ConnectionHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.*;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http2.Http2CodecUtil.getEmbeddedHttp2Exception;

/**
 * @Author tanghong
 * @Date 18-8-14-下午8:42
 * @Version 1.0
 */
public class AbstractNettyProxyHandler extends GrpcHttp2ConnectionHandler {
    private static final long GRACEFUL_SHUTDOWN_NO_TIMEOUT = -1;
    private boolean autoTuneFlowControlOn = false;
    private int initialConnectionWindow;
    private ChannelHandlerContext ctx;
    private final AbstractNettyProxyHandler.FlowControlPinger flowControlPing = new AbstractNettyProxyHandler.FlowControlPinger();

    private static final long BDP_MEASUREMENT_PING = 1234;

    AbstractNettyProxyHandler(
            ChannelPromise channelUnused,
            Http2ConnectionDecoder decoder,
            Http2ConnectionEncoder encoder,
            Http2Settings initialSettings) {
        super(channelUnused, decoder, encoder, initialSettings);

        // During a graceful shutdown, wait until all streams are closed.
        gracefulShutdownTimeoutMillis(GRACEFUL_SHUTDOWN_NO_TIMEOUT);

        // Extract the connection window from the settings if it was set.
        this.initialConnectionWindow = initialSettings.initialWindowSize() == null ? -1 :
                initialSettings.initialWindowSize();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
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
    AbstractNettyProxyHandler.FlowControlPinger flowControlPing() {
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

}
