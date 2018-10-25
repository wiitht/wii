package org.wii.agency.mesh.client;

import com.linecorp.armeria.internal.AbstractHttp2ConnectionHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.*;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:12
 * @Version 1.0
 */
public class Http2ClientConnectionHandler extends Http2ConnectionExtHandler {

    private final Http2ResponseDecoder responseDecoder;
    private boolean closing;

    private static final Http2StreamVisitor closeAllStreams = stream -> {
        if (stream.state() != Http2Stream.State.CLOSED) {
            stream.close();
        }
        return true;
    };

    Http2ClientConnectionHandler(
            Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
            Http2Settings initialSettings, Http2ResponseDecoder responseDecoder) {

        super(decoder, encoder, initialSettings);
        this.responseDecoder = responseDecoder;
        connection().addListener(responseDecoder);
        decoder().frameListener(responseDecoder);
    }

    Http2ResponseDecoder responseDecoder() {
        return responseDecoder;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        // NB: Http2ConnectionHandler does not flush the preface string automatically.
        ctx.flush();
    }
    /**
     * Returns {@code true} if {@link ChannelHandlerContext#close()} has been called.
     */
    public boolean isClosing() {
        return closing;
    }


    protected void onCloseRequest(ChannelHandlerContext ctx) throws Exception {
        HttpSession.get(ctx.channel()).deactivate();
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        closing = true;

        // TODO(trustin): Remove this line once https://github.com/netty/netty/issues/4210 is fixed.
        connection().forEachActiveStream(closeAllStreams);

        onCloseRequest(ctx);
        super.close(ctx, promise);
    }
}
