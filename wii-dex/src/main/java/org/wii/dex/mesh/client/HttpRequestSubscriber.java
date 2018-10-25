package org.wii.dex.mesh.client;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.WriteTimeoutException;
import com.linecorp.armeria.common.*;
import com.linecorp.armeria.common.logging.RequestLogBuilder;
import com.linecorp.armeria.common.stream.AbortedStreamException;
import com.linecorp.armeria.common.stream.ClosedPublisherException;
import com.linecorp.armeria.common.util.Exceptions;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.util.ReferenceCountUtil;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:30
 * @Version 1.0
 */
public class HttpRequestSubscriber implements Subscriber<HttpObject>, ChannelFutureListener {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestSubscriber.class);

    enum State {
        NEEDS_TO_WRITE_FIRST_HEADER,
        NEEDS_DATA_OR_TRAILING_HEADERS,
        DONE
    }

    private final Channel ch;
    private final HttpObjectEncoder encoder;
    private final int id;
    private final HttpRequest request;
    private final HttpResponseDecoder.HttpResponseWrapper response;
    private final ClientRequestContext reqCtx;
    private final RequestLogBuilder logBuilder;
    private final long timeoutMillis;
    @Nullable
    private Subscription subscription;
    @Nullable
    private ScheduledFuture<?> timeoutFuture;
    private HttpRequestSubscriber.State state = HttpRequestSubscriber.State.NEEDS_TO_WRITE_FIRST_HEADER;

    HttpRequestSubscriber(Channel ch, HttpObjectEncoder encoder,
                          int id, HttpRequest request, HttpResponseDecoder.HttpResponseWrapper response,
                          ClientRequestContext reqCtx, long timeoutMillis) {

        this.ch = ch;
        this.encoder = encoder;
        this.id = id;
        this.request = request;
        this.response = response;
        this.reqCtx = reqCtx;
        logBuilder = reqCtx.logBuilder();
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Invoked on each write of an {@link HttpObject}.
     */
    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
            if (state == HttpRequestSubscriber.State.DONE) {
                // Successfully sent the request; schedule the response timeout.
                response.scheduleTimeout(ch.eventLoop());
            } else {
                assert subscription != null;
                subscription.request(1);
            }
            return;
        }

        fail(future.cause());

        final Throwable cause = future.cause();
        if (!(cause instanceof ClosedPublisherException)) {
            final Channel ch = future.channel();
            Exceptions.logIfUnexpected(logger, ch, HttpSession.get(ch).protocol(), cause);
            ch.close();
        }
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        assert this.subscription == null;
        this.subscription = subscription;

        final EventLoop eventLoop = ch.eventLoop();
        if (timeoutMillis > 0) {
            timeoutFuture = eventLoop.schedule(
                    () -> {
                        if (state == HttpRequestSubscriber.State.NEEDS_TO_WRITE_FIRST_HEADER) {
                            if (reqCtx instanceof AbstractRequestContext) {
                                ((AbstractRequestContext) reqCtx).setTimedOut();
                            }
                            failAndRespond(WriteTimeoutException.get());
                        }
                    },
                    timeoutMillis, TimeUnit.MILLISECONDS);
        }

        // NB: This must be invoked at the end of this method because otherwise the callback methods in this
        //     class can be called before the member fields (subscription and timeoutFuture) are initialized.
        //     It is because the successful write of the first headers will trigger subscription.request(1).
        eventLoop.execute(this::writeFirstHeader);
    }

    private void writeFirstHeader() {
        final HttpSession session = HttpSession.get(ch);
        if (!session.isActive()) {
            failAndRespond(ClosedSessionException.get());
            return;
        }

        final HttpHeaders firstHeaders = autoFillHeaders(ch);

        final SessionProtocol protocol = session.protocol();
        assert protocol != null;
        logBuilder.startRequest(ch, protocol);
        logBuilder.requestHeaders(firstHeaders);

        if (request.isEmpty()) {
            setDone();
            write0(firstHeaders, true, true);
        } else {
            write0(firstHeaders, false, true);
        }
        state = HttpRequestSubscriber.State.NEEDS_DATA_OR_TRAILING_HEADERS;
        cancelTimeout();
    }

    private HttpHeaders autoFillHeaders(Channel ch) {
        HttpHeaders requestHeaders = request.headers();
        if (requestHeaders.isImmutable()) {
            final HttpHeaders temp = requestHeaders;
            requestHeaders = new DefaultHttpHeaders(false);
            requestHeaders.set(temp);
        }

        final HttpHeaders additionalHeaders = reqCtx.additionalRequestHeaders();
        if (!additionalHeaders.isEmpty()) {
            requestHeaders.setAllIfAbsent(additionalHeaders);
        }

        final SessionProtocol sessionProtocol = reqCtx.sessionProtocol();
        if (requestHeaders.authority() == null) {
            final InetSocketAddress isa = (InetSocketAddress) ch.remoteAddress();
            final String hostname = isa.getHostName();
            final int port = isa.getPort();

            final String authority;
            if (port == sessionProtocol.defaultPort()) {
                authority = hostname;
            } else {
                final StringBuilder buf = new StringBuilder(hostname.length() + 6);
                buf.append(hostname);
                buf.append(':');
                buf.append(port);
                authority = buf.toString();
            }

            requestHeaders.authority(authority);
        }

        if (requestHeaders.scheme() == null) {
            requestHeaders.scheme(sessionProtocol.isTls() ? "https" : "http");
        }

        if (!requestHeaders.contains(HttpHeaderNames.USER_AGENT)) {
            requestHeaders.set(HttpHeaderNames.USER_AGENT, HttpHeaderUtil.USER_AGENT.toString());
        }
        return requestHeaders;
    }

    @Override
    public void onNext(HttpObject o) {
        if (!(o instanceof HttpData) && !(o instanceof HttpHeaders)) {
            throw newIllegalStateException(
                    "published an HttpObject that's neither Http2Headers nor Http2Data: " + o);
        }

        boolean endOfStream = o.isEndOfStream();
        switch (state) {
            case NEEDS_DATA_OR_TRAILING_HEADERS: {
                if (o instanceof HttpHeaders) {
                    final HttpHeaders trailingHeaders = (HttpHeaders) o;
                    if (trailingHeaders.status() != null) {
                        throw newIllegalStateException("published a trailing HttpHeaders with status: " + o);
                    }
                    // Trailing headers always end the stream even if not explicitly set.
                    endOfStream = true;
                }
                break;
            }
            case DONE:
                ReferenceCountUtil.safeRelease(o);
                return;
        }

        write(o, endOfStream, true);
    }

    @Override
    public void onError(Throwable cause) {
        failAndRespond(cause);
    }

    @Override
    public void onComplete() {
        if (!cancelTimeout()) {
            return;
        }

        if (state != HttpRequestSubscriber.State.DONE) {
            write(HttpData.EMPTY_DATA, true, true);
        }
    }

    private void write(HttpObject o, boolean endOfStream, boolean flush) {
        if (!ch.isActive()) {
            ReferenceCountUtil.safeRelease(o);
            fail(ClosedSessionException.get());
            return;
        }

        if (endOfStream) {
            setDone();
        }

        ch.eventLoop().execute(() -> write0(o, endOfStream, flush));
    }

    private void write0(HttpObject o, boolean endOfStream, boolean flush) {
        final ChannelFuture future;
        if (o instanceof HttpData) {
            final HttpData data = (HttpData) o;
            future = encoder.writeData(id, streamId(), data, endOfStream);
            logBuilder.increaseRequestLength(data.length());
        } else if (o instanceof HttpHeaders) {
            // 添加一个分割符号
            //encoder.write(DelimiterUtil.getDefault());
            future = encoder.writeHeaders(id, streamId(), (HttpHeaders) o, endOfStream);
            //ch.write(DelimiterUtil.getDefault());
        } else {
            // Should never reach here because we did validation in onNext().
            throw new Error();
        }

        if (endOfStream) {
            logBuilder.endRequest();
        }

        future.addListener(this);
        if (flush) {
            ch.flush();
        }

        if (state == HttpRequestSubscriber.State.DONE) {
            assert subscription != null;
            subscription.cancel();
        }
    }

    private int streamId() {
        return (id << 1) + 1;
    }

    private void fail(Throwable cause) {
        setDone();
        logBuilder.endRequest(cause);
        logBuilder.endResponse(cause);
        assert subscription != null;
        subscription.cancel();
    }

    private void setDone() {
        cancelTimeout();
        state = HttpRequestSubscriber.State.DONE;
    }

    private void failAndRespond(Throwable cause) {
        fail(cause);

        final Http2Error error;
        if (response.isOpen()) {
            response.close(cause);
            error = Http2Error.INTERNAL_ERROR;
        } else if (cause instanceof WriteTimeoutException || cause instanceof AbortedStreamException) {
            error = Http2Error.CANCEL;
        } else {
            Exceptions.logIfUnexpected(logger, ch,
                    HttpSession.get(ch).protocol(),
                    "a request publisher raised an exception", cause);
            error = Http2Error.INTERNAL_ERROR;
        }

        if (ch.isActive()) {
            encoder.writeReset(id, streamId(), error);
            ch.flush();
        }
    }

    private boolean cancelTimeout() {
        final ScheduledFuture<?> timeoutFuture = this.timeoutFuture;
        if (timeoutFuture == null) {
            return true;
        }

        this.timeoutFuture = null;
        return timeoutFuture.cancel(false);
    }

    private IllegalStateException newIllegalStateException(String msg) {
        final IllegalStateException cause = new IllegalStateException(msg);
        fail(cause);
        return cause;
    }
}
