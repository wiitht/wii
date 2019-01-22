package org.wiitht.wii.dex.proxy;

import io.grpc.Attributes;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.internal.AbstractServerStream;
import io.grpc.internal.StatsTraceContext;
import io.grpc.internal.TransportTracer;
import io.grpc.internal.WritableBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @Author tanghong
 * @Date 18-8-15-下午4:25
 * @Version 1.0
 */
public class NettyProxyServerStream extends AbstractServerStream {
    private static final Logger log = Logger.getLogger(NettyProxyServerStream.class.getName());

    private final NettyProxyServerStream.Sink sink = new NettyProxyServerStream.Sink();
    private final NettyProxyServerStream.TransportState state;
    private final Channel channel;
    private final WriteQueue writeQueue;
    private final Attributes attributes;
    private final String authority;
    private final TransportTracer transportTracer;

    public NettyProxyServerStream(
            Channel channel,
            NettyProxyServerStream.TransportState state,
            Attributes transportAttrs,
            String authority,
            StatsTraceContext statsTraceCtx,
            TransportTracer transportTracer) {
        super(new NettyProxyWritableBufferAllocator(channel.alloc()), statsTraceCtx);
        this.state = checkNotNull(state, "transportState");
        this.channel = checkNotNull(channel, "channel");
        this.writeQueue = state.handler.getWriteQueue();
        this.attributes = checkNotNull(transportAttrs);
        this.authority = authority;
        this.transportTracer = checkNotNull(transportTracer, "transportTracer");
    }

    @Override
    protected NettyProxyServerStream.TransportState transportState() {
        return state;
    }

    @Override
    protected NettyProxyServerStream.Sink abstractServerStreamSink() {
        return sink;
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public String getAuthority() {
        return authority;
    }

    private class Sink implements AbstractServerStream.Sink {
        @Override
        public void request(final int numMessages) {
            if (channel.eventLoop().inEventLoop()) {
                // Processing data read in the event loop so can call into the deframer immediately
                transportState().requestMessagesFromDeframer(numMessages);
            } else {
                channel.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        transportState().requestMessagesFromDeframer(numMessages);
                    }
                });
            }
        }

        @Override
        public void writeHeaders(Metadata headers) {
            /*writeQueue.enqueue(
                    SendResponseHeadersCommand.createHeaders(
                            transportState(),
                            Utils.convertServerHeaders(headers)),
                    true);*/
        }

        @Override
        public void writeFrame(WritableBuffer frame, boolean flush, final int numMessages) {
           /* Preconditions.checkArgument(numMessages >= 0);
            if (frame == null) {
                writeQueue.scheduleFlush();
                return;
            }
            ByteBuf bytebuf = ((NettyWritableBuffer) frame).bytebuf();
            final int numBytes = bytebuf.readableBytes();
            // Add the bytes to outbound flow control.
            onSendingBytes(numBytes);
            writeQueue.enqueue(
                    new SendGrpcFrameCommand(transportState(), bytebuf, false),
                    channel.newPromise().addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            // Remove the bytes from outbound flow control, optionally notifying
                            // the client that they can send more bytes.
                            transportState().onSentBytes(numBytes);
                            if (future.isSuccess()) {
                                transportTracer.reportMessageSent(numMessages);
                            }
                        }
                    }), flush);*/
        }

        @Override
        public void writeTrailers(Metadata trailers, boolean headersSent, Status status) {
            Http2Headers http2Trailers = Utils.convertTrailers(trailers, headersSent);
            /*writeQueue.enqueue(
                    SendResponseHeadersCommand.createTrailers(transportState(), http2Trailers, status),
                    true);*/
        }

        @Override
        public void cancel(Status status) {
            //writeQueue.enqueue(new CancelServerStreamCommand(transportState(), status), true);
        }
    }

    /** This should only called from the transport thread. */
    public static class TransportState extends AbstractServerStream.TransportState {
        private final Http2Stream http2Stream;
        private final NettyProxyHandler handler;
        private final EventLoop eventLoop;

        public TransportState(
                NettyProxyHandler handler,
                EventLoop eventLoop,
                Http2Stream http2Stream,
                int maxMessageSize,
                StatsTraceContext statsTraceCtx,
                TransportTracer transportTracer) {
            super(maxMessageSize, statsTraceCtx, transportTracer);
            this.http2Stream = checkNotNull(http2Stream, "http2Stream");
            this.handler = checkNotNull(handler, "handler");
            this.eventLoop = eventLoop;
        }

        @Override
        public void runOnTransportThread(final Runnable r) {
            if (eventLoop.inEventLoop()) {
                r.run();
            } else {
                eventLoop.execute(r);
            }
        }

        @Override
        public void bytesRead(int processedBytes) {
            handler.returnProcessedBytes(http2Stream, processedBytes);
            handler.getWriteQueue().scheduleFlush();
        }

        @Override
        public void deframeFailed(Throwable cause) {
            log.log(Level.WARNING, "Exception processing message", cause);
            Status status = Status.fromThrowable(cause);
            transportReportStatus(status);
            //handler.getWriteQueue().enqueue(new CancelServerStreamCommand(this, status), true);
        }

        void inboundDataReceived(ByteBuf frame, boolean endOfStream) {
            //super.inboundDataReceived(new NettyReadableBuffer(frame.retain()), endOfStream);
        }
    }
}
