package org.wiitht.wii.dex.trail;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import org.wiitht.wii.dex.proxy.WriteQueue;


import java.util.List;

import static io.netty.handler.codec.http2.DefaultHttp2LocalFlowController.DEFAULT_WINDOW_UPDATE_RATIO;

/**
 * @Author tanghong
 * @Date 18-8-21-下午5:21
 * @Version 1.0
 */
public class GrpcProxyClientHandler extends Http2ConnectionHandler {

    private final Http2Connection.PropertyKey streamKey;
    private final GrpcProxyListener proxyListener;

    private static final int DEFAULT_FLOW_CONTROL_WINDOW = 1048576; // 1MiB
    private static final int DEFAULT_MAX_HEADER_LIST_SIZE = 8192;


    public static GrpcProxyClientHandler createHandler(WriteQueue queue){
        Http2HeadersDecoder headersDecoder = new GrpcHttp2HeadersUtils.GrpcHttp2ClientHeadersDecoder(DEFAULT_MAX_HEADER_LIST_SIZE);
        Http2FrameReader frameReader = new DefaultHttp2FrameReader(headersDecoder);
        Http2FrameWriter frameWriter = new DefaultHttp2FrameWriter();
        Http2Connection connection = new DefaultHttp2Connection(false);
        WeightedFairQueueByteDistributor dist = new WeightedFairQueueByteDistributor(connection);
        dist.allocationQuantum(16 * 1024);
        DefaultHttp2RemoteFlowController controller =
                new DefaultHttp2RemoteFlowController(connection, dist);
        connection.remote().flowController(controller);

        Http2FrameLogger frameLogger = new Http2FrameLogger(LogLevel.DEBUG, GrpcProxyClientHandler.class);

        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, new Http2OutboundFrameLogger(frameWriter, frameLogger));

        // Create the local flow controller configured to auto-refill the connection window.
        connection.local().flowController(
                new DefaultHttp2LocalFlowController(connection, DEFAULT_WINDOW_UPDATE_RATIO, true));

        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, new Http2InboundFrameLogger(frameReader, frameLogger));

        Http2Settings settings = new Http2Settings();
        settings.pushEnabled(false);
        settings.initialWindowSize(DEFAULT_FLOW_CONTROL_WINDOW);
        settings.maxConcurrentStreams(0);
        settings.maxHeaderListSize(DEFAULT_MAX_HEADER_LIST_SIZE);
        return new GrpcProxyClientHandler(connection, decoder, encoder, settings, queue);
    }

    private GrpcProxyClientHandler(Http2Connection connection, Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings, WriteQueue queue) {
        super(decoder, encoder, initialSettings);
        decoder().frameListener(new GrpcProxyClientHandler.FrameListener());
        streamKey = connection.newKey();
        proxyListener = new GrpcProxyListener(queue);
    }


    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
            throws Exception {
        if (msg instanceof SendHeaderCommand) {
            sendHeaderFrame(ctx, (SendHeaderCommand) msg, promise);
        } else if (msg instanceof SendFrameCommand) {
            sendGrpcFrame(ctx, (SendFrameCommand) msg, promise);
        } else if (msg instanceof SendDataCommand){
            sendDataFrame(ctx, (SendDataCommand)msg, promise);
        }
        else {
            throw new AssertionError("Write called for unexpected type: " + msg.getClass().getName());
        }
    }

    private void sendDataFrame(ChannelHandlerContext ctx, SendDataCommand cmd, ChannelPromise promise){
        ctx.write(cmd.content(), promise);
    }

    private void sendHeaderFrame(ChannelHandlerContext ctx, SendHeaderCommand cmd,
                               ChannelPromise promise) {
        // Call the base class to write the HTTP/2 DATA frame.
        // Note: no need to flush since this is handled by the outbound flow controller.
        encoder().writeHeaders(ctx, cmd.stream().id(), cmd.headers(), 0, false, ctx.newPromise());
    }

    private void sendGrpcFrame(ChannelHandlerContext ctx, SendFrameCommand cmd,
                               ChannelPromise promise) {
        // Call the base class to write the HTTP/2 DATA frame.
        // Note: no need to flush since this is handled by the outbound flow controller.
        encoder().writeData(ctx, cmd.streamId(), cmd.content(), 0, true, promise);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        proxyListener.onData(in);
    }

    private class FrameListener extends Http2FrameAdapter {

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                              boolean endOfStream) throws Http2Exception {
            //proxyListener.onMessage(streamId, data);
            //NettyClientHandler.this.onDataRead(streamId, data, padding, endOfStream);
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
            //proxyListener.onHeaders(streamId, headers);
            //NettyClientHandler.this.onHeadersRead(streamId, headers, endStream);
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                throws Http2Exception {
            //NettyClientHandler.this.onRstStreamRead(streamId, errorCode);
        }

    }

}
