package org.wiitht.wii.dex.mesh.client;

import com.linecorp.armeria.internal.IdleTimeoutHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:41
 * @Version 1.0
 */
public class HttpClientIdleTimeoutHandler extends IdleTimeoutHandler {

    HttpClientIdleTimeoutHandler(long idleTimeoutMillis) {
        super("client", idleTimeoutMillis);
    }

    @Override
    protected boolean hasRequestsInProgress(ChannelHandlerContext ctx) {
        return HttpSession.get(ctx.channel()).hasUnfinishedResponses();
    }
}
