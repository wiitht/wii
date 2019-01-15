package org.wiitht.wii.eel.mesh.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.*;
import io.netty.util.CharsetUtil;

import java.util.List;

import static io.netty.buffer.ByteBufUtil.hexDump;
import static io.netty.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2FrameTypes.SETTINGS;
import static java.lang.Math.min;

/**
 * @Author tanghong
 * @Date 18-10-24-下午2:39
 * @Version 1.0
 */
public class Http2ConnectionExtHandler extends Http2ConnectionHandler {

    private BaseDecoder byteDecoder;
    private Http2Settings initialSettings;
    private static final ByteBuf HTTP_1_X_BUF = Unpooled.unreleasableBuffer(
            Unpooled.wrappedBuffer(new byte[] {'H', 'T', 'T', 'P', '/', '1', '.'})).asReadOnly();

    protected Http2ConnectionExtHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
        this.initialSettings = initialSettings;
    }


    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // Initialize the encoder, decoder, flow controllers, and internal state.
        encoder().lifecycleManager(this);
        decoder().lifecycleManager(this);
        encoder().flowController().channelHandlerContext(ctx);
        decoder().flowController().channelHandlerContext(ctx);
        byteDecoder = new PrefaceDecoder(ctx);
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        if (byteDecoder != null) {
            byteDecoder.handlerRemoved(ctx);
            byteDecoder = null;
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (byteDecoder == null) {
            byteDecoder = new PrefaceDecoder(ctx);
        }
        byteDecoder.channelActive(ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Call super class first, as this may result in decode being called.
        super.channelInactive(ctx);
        if (byteDecoder != null) {
            byteDecoder.channelInactive(ctx);
            byteDecoder = null;
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byteDecoder.decode(ctx, in, out);
    }

    private abstract class BaseDecoder {
        public abstract void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception;
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception { }
        public void channelActive(ChannelHandlerContext ctx) throws Exception { }

        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // Connection has terminated, close the encoder and decoder.
            encoder().close();
            decoder().close();

            // We need to remove all streams (not just the active ones).
            // See https://github.com/netty/netty/issues/4838.
            connection().close(ctx.voidPromise());
        }

        /**
         * Determine if the HTTP/2 connection preface been sent.
         */
        public boolean prefaceSent() {
            return true;
        }
    }

    private static ByteBuf clientPrefaceString(Http2Connection connection) {
        return connection.isServer() ? connectionPrefaceBuf() : null;
    }

    private final class PrefaceDecoder extends BaseDecoder {
        private ByteBuf clientPrefaceString;
        private boolean prefaceSent;


        public PrefaceDecoder(ChannelHandlerContext ctx) throws Exception {
            clientPrefaceString = clientPrefaceString(encoder().connection());
            // This handler was just added to the context. In case it was handled after
            // the connection became active, send the connection preface now.
            sendPreface(ctx);
        }

        @Override
        public boolean prefaceSent() {
            return prefaceSent;
        }

        @Override
        public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            try {
                if (ctx.channel().isActive() && readClientPrefaceString(in) && verifyFirstFrameIsSettings(in)) {
                    // After the preface is read, it is time to hand over control to the post initialized decoder.
                    byteDecoder = new FrameDecoder();
                    byteDecoder.decode(ctx, in, out);
                }
            } catch (Throwable e) {
                onError(ctx, false, e);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // The channel just became active - send the connection preface to the remote endpoint.
            sendPreface(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            cleanup();
            super.channelInactive(ctx);
        }

        /**
         * Releases the {@code clientPrefaceString}. Any active streams will be left in the open.
         */
        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            cleanup();
        }

        /**
         * Releases the {@code clientPrefaceString}. Any active streams will be left in the open.
         */
        private void cleanup() {
            if (clientPrefaceString != null) {
                clientPrefaceString.release();
                clientPrefaceString = null;
            }
        }

        /**
         * Decodes the client connection preface string from the input buffer.
         *
         * @return {@code true} if processing of the client preface string is complete. Since client preface strings can
         *         only be received by servers, returns true immediately for client endpoints.
         */
        private boolean readClientPrefaceString(ByteBuf in) throws Http2Exception {
            if (clientPrefaceString == null) {
                return true;
            }

            int prefaceRemaining = clientPrefaceString.readableBytes();
            int bytesRead = min(in.readableBytes(), prefaceRemaining);

            // If the input so far doesn't match the preface, break the connection.
            if (bytesRead == 0 || !ByteBufUtil.equals(in, in.readerIndex(),
                    clientPrefaceString, clientPrefaceString.readerIndex(),
                    bytesRead)) {
                int maxSearch = 1024; // picked because 512 is too little, and 2048 too much
                int http1Index =
                        ByteBufUtil.indexOf(HTTP_1_X_BUF, in.slice(in.readerIndex(), min(in.readableBytes(), maxSearch)));
                if (http1Index != -1) {
                    String chunk = in.toString(in.readerIndex(), http1Index - in.readerIndex(), CharsetUtil.US_ASCII);
                    throw connectionError(PROTOCOL_ERROR, "Unexpected HTTP/1.x request: %s", chunk);
                }
                String receivedBytes = hexDump(in, in.readerIndex(),
                        min(in.readableBytes(), clientPrefaceString.readableBytes()));
                throw connectionError(PROTOCOL_ERROR, "HTTP/2 client preface string missing or corrupt. " +
                        "Hex dump for received bytes: %s", receivedBytes);
            }
            in.skipBytes(bytesRead);
            clientPrefaceString.skipBytes(bytesRead);

            if (!clientPrefaceString.isReadable()) {
                // Entire preface has been read.
                clientPrefaceString.release();
                clientPrefaceString = null;
                return true;
            }
            return false;
        }

        /**
         * Peeks at that the next frame in the buffer and verifies that it is a non-ack {@code SETTINGS} frame.
         *
         * @param in the inbound buffer.
         * @return {@code} true if the next frame is a non-ack {@code SETTINGS} frame, {@code false} if more
         * data is required before we can determine the next frame type.
         * @throws Http2Exception thrown if the next frame is NOT a non-ack {@code SETTINGS} frame.
         */
        private boolean verifyFirstFrameIsSettings(ByteBuf in) throws Http2Exception {
            if (in.readableBytes() < 5) {
                // Need more data before we can see the frame type for the first frame.
                return false;
            }

            short frameType = in.getUnsignedByte(in.readerIndex() + 3);
            short flags = in.getUnsignedByte(in.readerIndex() + 4);
            if (frameType != SETTINGS || (flags & Http2Flags.ACK) != 0) {
                throw connectionError(PROTOCOL_ERROR, "First received frame was not SETTINGS. " +
                                "Hex dump for first 5 bytes: %s",
                        hexDump(in, in.readerIndex(), 5));
            }
            return true;
        }

        /**
         * Sends the HTTP/2 connection preface upon establishment of the connection, if not already sent.
         */
        private void sendPreface(ChannelHandlerContext ctx) throws Exception {
            if (prefaceSent || !ctx.channel().isActive()) {
                return;
            }

            prefaceSent = true;

            //ctx.write(DelimiterUtil.getDefault());

            final boolean isClient = !connection().isServer();
            if (isClient) {
                // Clients must send the preface string as the first bytes on the connection.
                ctx.write(connectionPrefaceBuf()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }

            // Both client and server must send their initial settings.
            encoder().writeSettings(ctx, initialSettings, ctx.newPromise()).addListener(
                    ChannelFutureListener.CLOSE_ON_FAILURE);

            if (isClient) {
                // If this handler is extended by the user and we directly fire the userEvent from this context then
                // the user will not see the event. We should fire the event starting with this handler so this class
                // (and extending classes) have a chance to process the event.
                //userEventTriggered(ctx, Http2ConnectionPrefaceAndSettingsFrameWrittenEvent.INSTANCE);
            }
        }
    }

    private final class FrameDecoder extends BaseDecoder {
        @Override
        public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            try {
                decoder().decodeFrame(ctx, in, out);
            } catch (Throwable e) {
                onError(ctx, false, e);
            }
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        promise = promise.unvoid();
        // Avoid NotYetConnectedException
        if (!ctx.channel().isActive()) {
            ctx.close(promise);
            return;
        }

        // If the user has already sent a GO_AWAY frame they may be attempting to do a graceful shutdown which requires
        // sending multiple GO_AWAY frames. We should only send a GO_AWAY here if one has not already been sent. If
        // a GO_AWAY has been sent we send a empty buffer just so we can wait to close until all other data has been
        // flushed to the OS.
        // https://github.com/netty/netty/issues/5307
        // 关闭会写部分数据到服务端
        //final ChannelFuture future = connection().goAwaySent() ? ctx.write(EMPTY_BUFFER) : goAway(ctx, null);
        ctx.flush();
        //doGracefulShutdown(ctx, future, promise);
    }
}
