package org.wiitht.wii.core.internal.client;

import org.wiitht.wii.core.internal.manage.Deliverer;
import org.wiitht.wii.core.internal.message.GrpcHttp2HeadersDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;

import java.util.List;

import static io.netty.handler.codec.http2.DefaultHttp2LocalFlowController.DEFAULT_WINDOW_UPDATE_RATIO;

/**
 * @Author wii
 * @Date 18-12-24-下午3:20
 * @Version 1.0
 */
public class ClientH2ConnectionHandler extends Http2ConnectionHandler {

    private Deliverer deliverer;

    private static final int DEFAULT_FLOW_CONTROL_WINDOW = 1048576; // 1MiB
    private static final int DEFAULT_MAX_HEADER_LIST_SIZE = 8192;


    public static ClientH2ConnectionHandler newHandler(Deliverer deliverer){
        Http2HeadersDecoder headersDecoder = new GrpcHttp2HeadersDecoder.GrpcHttp2ClientHeadersDecoder(DEFAULT_MAX_HEADER_LIST_SIZE);
        Http2FrameReader frameReader = new DefaultHttp2FrameReader(headersDecoder);
        Http2FrameWriter frameWriter = new DefaultHttp2FrameWriter();
        Http2Connection connection = new DefaultHttp2Connection(false);
        WeightedFairQueueByteDistributor dist = new WeightedFairQueueByteDistributor(connection);
        dist.allocationQuantum(16 * 1024);
        DefaultHttp2RemoteFlowController controller =
                new DefaultHttp2RemoteFlowController(connection, dist);
        connection.remote().flowController(controller);

        Http2FrameLogger frameLogger = new Http2FrameLogger(LogLevel.DEBUG, ClientH2ConnectionHandler.class);

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
        return new ClientH2ConnectionHandler(connection, decoder, encoder, settings, deliverer);
    }

    private ClientH2ConnectionHandler(Http2Connection connection, Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings, Deliverer deliverer) {
        super(decoder, encoder, initialSettings);
        this.deliverer = deliverer;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
            throws Exception {
        if (msg instanceof DeliverCommand.DeliverHeaderCommand) {
            writeHeaderFrame(ctx, (DeliverCommand.DeliverHeaderCommand) msg, promise);
        } else if (msg instanceof DeliverCommand.DeliverDataCommand) {
            writeDataFrame(ctx, (DeliverCommand.DeliverDataCommand) msg, promise);
        } else {
            throw new AssertionError("Write called for unexpected type: " + msg.getClass().getName());
        }
    }

    private void writeDataFrame(ChannelHandlerContext ctx, DeliverCommand.DeliverDataCommand cmd, ChannelPromise promise){
        //ctx.write(cmd.content(), promise);
        encoder().writeData(ctx, cmd.getStream().id(), cmd.content(), 0, true, promise);
    }

    private void writeHeaderFrame(ChannelHandlerContext ctx, DeliverCommand.DeliverHeaderCommand cmd,
                                  ChannelPromise promise) {
        // Call the base class to write the HTTP/2 DATA frame.
        // Note: no need to flush since this is handled by the outbound flow controller.
        encoder().writeHeaders(ctx, cmd.stream().id(), cmd.headers(), 0, false, ctx.newPromise());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        deliverer.getReceiver().response(in);
    }
}