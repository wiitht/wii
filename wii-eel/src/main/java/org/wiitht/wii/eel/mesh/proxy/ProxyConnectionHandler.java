package org.wiitht.wii.eel.mesh.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.netty.util.CharsetUtil;
import org.wiitht.wii.eel.proxy.WriteQueue;
import org.wiitht.wii.eel.proxy.SendDataCommand;
import org.wiitht.wii.eel.trail.GrpcProxyClient;

import java.net.SocketAddress;
import java.util.List;

import static io.netty.buffer.ByteBufUtil.hexDump;
import static io.netty.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;
import static io.netty.handler.codec.http2.Http2CodecUtil.readUnsignedInt;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2FrameTypes.SETTINGS;
import static java.lang.Math.min;

/**
 * @Author tanghong
 * @Date 18-10-24-下午4:20
 * @Version 1.0
 */
public class ProxyConnectionHandler extends ByteToMessageDecoder implements ChannelOutboundHandler {
    private DefaultHttp2ConnectionEncoder encoder;
    private DefaultHttp2ConnectionDecoder decoder;
    private Http2Settings http2Settings;
    private WriteQueue serverWriteQueue;
    private static final int DEFAULT_FLOW_CONTROL_WINDOW = 1048576; // 1MiB

    public ProxyConnectionHandler() {
        Http2Connection connection = new DefaultHttp2Connection(true);
        Http2FrameLogger frameLogger = new Http2FrameLogger(LogLevel.DEBUG, ProxyConnectionHandler.class);
        Http2FrameWriter frameWriter = new Http2OutboundFrameLogger(new DefaultHttp2FrameWriter(), frameLogger);
        Http2FrameReader frameReader = new Http2InboundFrameLogger(new DefaultHttp2FrameReader(), frameLogger);
        encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, new Http2InboundFrameLogger(frameReader, frameLogger));

        http2Settings = new Http2Settings();
        //http2Settings.initialWindowSize(DEFAULT_FLOW_CONTROL_WINDOW);
        //http2Settings.maxConcurrentStreams(Integer.MAX_VALUE);
        //http2Settings.maxHeaderListSize(GrpcUtil.DEFAULT_MAX_HEADER_LIST_SIZE);
    }

    private static final ByteBuf HTTP_1_X_BUF = Unpooled.unreleasableBuffer(
            Unpooled.wrappedBuffer(new byte[] {'H', 'T', 'T', 'P', '/', '1', '.'})).asReadOnly();
    private BaseDecoder byteDecoder;

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                        ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {

    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof SendDataCommand) {
            SendDataCommand command = (SendDataCommand)msg;
            ctx.write(command.content());
        } else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // Trigger flush after read on the assumption that flush is cheap if there is nothing to write and that
        // for flow-control the read may release window that causes data to be written that can now be flushed.
        try {
            // First call channelReadComplete0(...) as this may produce more data that we want to flush
            channelReadComplete0(ctx);
        } finally {
            flush(ctx);
        }
    }

    private void channelReadComplete0(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byteDecoder.decode(ctx, in, out);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        serverWriteQueue = new WriteQueue(ctx.channel());
        // Initialize the encoder, decoder, flow controllers, and internal state.
        this.byteDecoder = new PrefaceDecoder(ctx);
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
        super.channelActive(ctx);
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
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        // Writability is expected to change while we are writing. We cannot allow this event to trigger reentering
        // the allocation and write loop. Reentering the event loop will lead to over or illegal allocation.
        try {
            if (ctx.channel().isWritable()) {
                flush(ctx);
            }
            //encoder.flowController().channelWritabilityChanged();
        } finally {
            super.channelWritabilityChanged(ctx);
        }
    }

    // 服务端暂时不需要,发送http2标识
    private static ByteBuf clientPrefaceString() {
        return connectionPrefaceBuf();
    }

    private abstract class BaseDecoder {
        public abstract void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception;
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception { }
        public void channelActive(ChannelHandlerContext ctx) throws Exception { }

        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // Connection has terminated, close the encoder and decoder.
            //encoder().close();
            //decoder().close();

            // We need to remove all streams (not just the active ones).
            // See https://github.com/netty/netty/issues/4838.
            //connection().close(ctx.voidPromise());
        }

        /**
         * Determine if the HTTP/2 connection preface been sent.
         */
        public boolean prefaceSent() {
            return true;
        }
    }

    private final class PrefaceDecoder extends BaseDecoder {
        private ByteBuf clientPrefaceString;
        private boolean prefaceSent;

        public PrefaceDecoder(ChannelHandlerContext ctx) throws Exception {
            clientPrefaceString = clientPrefaceString();
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

                    in.readByte();
                    in.readUnsignedByte();
                    readUnsignedInt(in);
                    in.readSlice(3);

                    encoder.writeSettingsAck(ctx, ctx.newPromise());
                    encoder.remoteSettings(http2Settings);
                    //byteDecoder.decode(ctx, in, out);
                }
            } catch (Throwable e) {
                //onError(ctx, false, e);
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
         * Sends the HTTP/2 connection preface upon establishment of the connection, if not already sent.
         */
        protected void sendPreface(ChannelHandlerContext ctx) throws Exception {
            if (prefaceSent || !ctx.channel().isActive()) {
                return;
            }

            prefaceSent = true;

            // Both client and server must send their initial settings.
            encoder.writeSettings(ctx, http2Settings, ctx.newPromise()).addListener(
                    ChannelFutureListener.CLOSE_ON_FAILURE);

        }
    }

    /**
     * Peeks at that the next frame in the buffer and verifies that it is a non-ack {@code SETTINGS} frame.
     *
     * @param in the inbound buffer.
     * @return {@code} true if the next frame is a non-ack {@code SETTINGS} frame, {@code false} if more
     * data is required before we can determine the next frame type.
     * @throws Http2Exception thrown if the next frame is NOT a non-ack {@code SETTINGS} frame.
     */
    protected boolean verifyFirstFrameIsSettings(ByteBuf in) throws Http2Exception {
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


    private final class FrameDecoder extends BaseDecoder {

        @Override
        public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            try {
                //boolean isSetting = verifyFirstFrameIsSettings(in);
                GrpcProxyClient.start(serverWriteQueue);
                GrpcProxyClient.sendData(new org.wiitht.wii.eel.trail.SendDataCommand(in));
                in.clear();
                //这里只需要对消息头解码即可,消息体转发到对应的机器中去；
                //decoder.decodeFrame(ctx, in, out);
            } catch (Throwable e) {
                //onError(ctx, false, e);
            }
        }

    }


}
