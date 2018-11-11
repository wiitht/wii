package org.wiitht.wii.dex.mesh.client;

import com.linecorp.armeria.client.*;
import com.linecorp.armeria.common.*;
import com.linecorp.armeria.internal.PathAndQuery;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.EventLoop;

import javax.annotation.Nullable;

import static com.linecorp.armeria.internal.ArmeriaHttpUtil.concatPaths;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:21
 * @Version 1.0
 */
public class DefaultHttpClient extends UserClient<HttpRequest, HttpResponse> implements HttpClient {

    DefaultHttpClient(ClientBuilderParams params, Client<HttpRequest, HttpResponse> delegate,
                      MeterRegistry meterRegistry, SessionProtocol sessionProtocol, Endpoint endpoint) {
        super(params, delegate, meterRegistry, sessionProtocol, endpoint);
    }

    @Override
    public HttpResponse execute(HttpRequest req) {
        return execute(null, req);
    }

    private HttpResponse execute(@Nullable EventLoop eventLoop, HttpRequest req) {
        final String concatPaths = concatPaths(uri().getRawPath(), req.path());
        req.path(concatPaths);

        final PathAndQuery pathAndQuery = PathAndQuery.parse(concatPaths);
        if (pathAndQuery == null) {
            req.abort();
            return HttpResponse.ofFailure(new IllegalArgumentException("invalid path: " + concatPaths));
        }

        return execute(eventLoop, req.method(), pathAndQuery.path(), pathAndQuery.query(), null, req, cause -> {
            final HttpResponseWriter res = HttpResponse.streaming();
            res.close(cause);
            return res;
        });
    }

    @Override
    public HttpResponse execute(AggregatedHttpMessage aggregatedReq) {
        return execute(null, aggregatedReq);
    }

    HttpResponse execute(@Nullable EventLoop eventLoop, AggregatedHttpMessage aggregatedReq) {
        return execute(eventLoop, HttpRequest.of(aggregatedReq));
    }
}
