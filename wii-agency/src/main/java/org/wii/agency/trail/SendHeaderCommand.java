package org.wii.agency.trail;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import io.grpc.Status;
import io.netty.handler.codec.http2.Http2Headers;

/**
 * @Author tanghong
 * @Date 18-8-21-下午6:59
 * @Version 1.0
 */
public class SendHeaderCommand extends ProxyWriteQueue.AbstractQueuedCommand{
    private final StreamIdHolder stream;
    private final Http2Headers headers;
    private final Status status;

    public SendHeaderCommand(StreamIdHolder stream, Http2Headers headers, Status status) {
        this.stream = Preconditions.checkNotNull(stream, "stream");
        this.headers = Preconditions.checkNotNull(headers, "headers");
        this.status = status;
    }

    public static SendHeaderCommand createHeaders(StreamIdHolder stream, Http2Headers headers) {
        return new SendHeaderCommand(stream, headers, null);
    }

    static SendHeaderCommand createTrailers(
            StreamIdHolder stream, Http2Headers headers, Status status) {
        return new SendHeaderCommand(
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
        if (that == null || !that.getClass().equals(SendHeaderCommand.class)) {
            return false;
        }
        SendHeaderCommand thatCmd = (SendHeaderCommand) that;
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
