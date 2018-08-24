package org.wii.agency.proxy;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import io.grpc.Status;
import io.netty.handler.codec.http2.Http2Headers;
import org.wii.agency.trail.StreamIdHolder;

/**
 * @Author tanghong
 * @Date 18-8-22-下午7:34
 * @Version 1.0
 */
public class SendProxyHeaderCommand extends WriteQueue.AbstractQueuedCommand{
    private final StreamIdHolder stream;
    private final Http2Headers headers;
    private final Status status;

    public SendProxyHeaderCommand(StreamIdHolder stream, Http2Headers headers, Status status) {
        this.stream = Preconditions.checkNotNull(stream, "stream");
        this.headers = Preconditions.checkNotNull(headers, "headers");
        this.status = status;
    }

    public static SendProxyHeaderCommand createHeaders(StreamIdHolder stream, Http2Headers headers) {
        return new SendProxyHeaderCommand(stream, headers, null);
    }

    static SendProxyHeaderCommand createTrailers(
            StreamIdHolder stream, Http2Headers headers, Status status) {
        return new SendProxyHeaderCommand(
                stream, headers, Preconditions.checkNotNull(status, "status"));
    }

    StreamIdHolder stream() {
        return stream;
    }

    Http2Headers headers() {
        return headers;
    }

    boolean endOfStream() {
        return status != null;
    }

    Status status() {
        return status;
    }

    @Override
    public boolean equals(Object that) {
        if (that == null || !that.getClass().equals(SendProxyHeaderCommand.class)) {
            return false;
        }
        SendProxyHeaderCommand thatCmd = (SendProxyHeaderCommand) that;
        return thatCmd.stream.equals(stream)
                && thatCmd.headers.equals(headers)
                && thatCmd.status.equals(status);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(stream=" + stream.id() + ", headers=" + headers
                + ", status=" + status + ")";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(stream, status);
    }

}

