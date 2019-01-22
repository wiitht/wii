package org.wiitht.wii.dex.mesh.client;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.ClosedSessionException;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.internal.InboundTrafficController;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

import javax.annotation.Nullable;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:15
 * @Version 1.0
 */
public interface HttpSession {
    HttpSession INACTIVE = new HttpSession() {

        private final InboundTrafficController inboundTrafficController =
                new InboundTrafficController(null, 0, 0);

        @Nullable
        @Override
        public SessionProtocol protocol() {
            return null;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public InboundTrafficController inboundTrafficController() {
            return inboundTrafficController;
        }

        @Override
        public boolean hasUnfinishedResponses() {
            return false;
        }

        @Override
        public boolean invoke(ClientRequestContext ctx, HttpRequest req, DecodedHttpResponse res) {
            res.close(ClosedSessionException.get());
            return false;
        }

        @Override
        public void retryWithH1C() {
            throw new IllegalStateException();
        }

        @Override
        public void deactivate() {}
    };

    static HttpSession get(Channel ch) {
        final ChannelHandler lastHandler = ch.pipeline().last();
        if (lastHandler instanceof HttpSession) {
            return (HttpSession) lastHandler;
        }

        for (ChannelHandler h : ch.pipeline().toMap().values()) {
            if (h instanceof HttpSession) {
                return (HttpSession) h;
            }
        }

        return INACTIVE;
    }

    @Nullable
    SessionProtocol protocol();

    boolean isActive();

    InboundTrafficController inboundTrafficController();

    boolean hasUnfinishedResponses();

    boolean invoke(ClientRequestContext ctx, HttpRequest req, DecodedHttpResponse res);

    void retryWithH1C();

    void deactivate();
}
