package org.wiitht.wii.core.internal.client;

/**
 * 投递命令
 * 1）命令类别分类HeaderCommand,DataCommand,ErrorCommand,FailCommand,RetryCommand,OvertimeCommand
 */
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import org.wiitht.wii.core.internal.message.MessageQueue;
import io.grpc.Status;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2Headers;
/**
 * @Author wii
 * @Date 18-12-24-下午3:20
 * @Version 1.0
 */
public class DeliverCommand {

    public class DefaultStreamIdHolder implements MessageQueue.StreamIdHolder{
        private int streamId;
        public DefaultStreamIdHolder(int streamId){
            this.streamId = streamId;
        }

        @Override
        public int id() {
            return this.streamId;
        }
    }

    public class DeliverHeaderCommand extends MessageQueue.AbstractQueuedCommand{
        private final MessageQueue.StreamIdHolder stream;
        private final Http2Headers headers;
        private final Status status;

        public DeliverHeaderCommand(MessageQueue.StreamIdHolder stream, Http2Headers headers, Status status) {
            this.stream = Preconditions.checkNotNull(stream, "stream");
            this.headers = Preconditions.checkNotNull(headers, "headers");
            this.status = status;
        }

        public DeliverHeaderCommand(MessageQueue.StreamIdHolder stream, Http2Headers headers) {
            this(stream, headers, null);
        }

        public DeliverHeaderCommand(int stream, Http2Headers headers) { this(new DefaultStreamIdHolder(stream), headers, null);
        }

        DeliverHeaderCommand createTrailers(
                MessageQueue.StreamIdHolder stream, Http2Headers headers, Status status) {
            return new DeliverHeaderCommand(stream, headers, Preconditions.checkNotNull(status, "status"));
        }

        MessageQueue.StreamIdHolder stream() {
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
            if (that == null || !that.getClass().equals(DeliverHeaderCommand.class)) {
                return false;
            }
            DeliverHeaderCommand thatCmd = (DeliverHeaderCommand) that;
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

    public class DeliverDataCommand extends DefaultByteBufHolder implements MessageQueue.QueuedCommand  {
        private final MessageQueue.StreamIdHolder stream;
        private final boolean endStream;
        private ChannelPromise promise;

        public DeliverDataCommand(int stream, ByteBuf data, boolean endStream) {
            super(data.copy());
            this.stream = new DefaultStreamIdHolder(stream);
            this.endStream = endStream;
        }

        public MessageQueue.StreamIdHolder getStream() {
            return stream;
        }

        public boolean isEndStream() {
            return endStream;
        }

        @Override
        public ChannelPromise promise() {
            return promise;
        }

        @Override
        public void promise(ChannelPromise promise) {
            this.promise = promise;
        }

        @Override
        public final void run(Channel channel) {
            channel.write(this, channel.newPromise());
        }
    }


}