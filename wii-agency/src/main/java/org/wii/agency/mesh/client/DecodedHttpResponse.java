package org.wii.agency.mesh.client;

import com.linecorp.armeria.common.DefaultHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpObject;
import com.linecorp.armeria.internal.InboundTrafficController;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.EventExecutor;

import javax.annotation.Nullable;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:13
 * @Version 1.0
 */
public class DecodedHttpResponse extends DefaultHttpResponse {

    private final EventLoop eventLoop;
    @Nullable
    private InboundTrafficController inboundTrafficController;
    private long writtenBytes;

    DecodedHttpResponse(EventLoop eventLoop) {
        this.eventLoop = eventLoop;
    }

    void init(InboundTrafficController inboundTrafficController) {
        this.inboundTrafficController = inboundTrafficController;
    }

    long writtenBytes() {
        return writtenBytes;
    }

    @Override
    protected EventExecutor defaultSubscriberExecutor() {
        return eventLoop;
    }

    @Override
    public boolean tryWrite(HttpObject obj) {
        final boolean published = super.tryWrite(obj);
        if (published && obj instanceof HttpData) {
            final int length = ((HttpData) obj).length();
            assert inboundTrafficController != null;
            inboundTrafficController.inc(length);
            writtenBytes += length;
        }
        return published;
    }

    @Override
    protected void onRemoval(HttpObject obj) {
        if (obj instanceof HttpData) {
            final int length = ((HttpData) obj).length();
            assert inboundTrafficController != null;
            inboundTrafficController.dec(length);
        }
    }
}

