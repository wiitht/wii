package org.wiitht.wii.eel.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.PlatformDependent;

import static io.netty.handler.codec.http2.Http2CodecUtil.*;
import static io.netty.handler.codec.http2.Http2FrameTypes.HEADERS;
import static java.lang.Math.min;
/**
 * @Author tanghong
 * @Date 18-8-16-上午10:48
 * @Version 1.0
 */
public class ProxyWrite {

    static void writeFrameHeaderInternal(ByteBuf out, int payloadLength, byte type,
                                         Http2Flags flags, int streamId) {
        out.writeMedium(payloadLength);
        out.writeByte(type);
        out.writeByte(flags.value());
        out.writeInt(streamId);
    }

    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId,
                                      Http2Headers headers, int padding, boolean endStream, ChannelPromise promise) {
        return writeHeadersInternal(ctx, streamId, headers, padding, endStream,
                false, 0, (short) 0, false, promise);
    }


    public ChannelFuture writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                                    Http2Flags flags, ByteBuf payload, ChannelPromise promise) {
        SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(promise, ctx.channel(), ctx.executor());
        try {
            //verifyStreamOrConnectionId(streamId, STREAM_ID);
            ByteBuf buf = ctx.alloc().buffer(FRAME_HEADER_LENGTH);
            // Assume nothing below will throw until buf is written. That way we don't have to take care of ownership
            // in the catch block.
            writeFrameHeaderInternal(buf, payload.readableBytes(), frameType, flags, streamId);
            ctx.write(buf, promiseAggregator.newPromise());
        } catch (Throwable t) {
            try {
                payload.release();
            } finally {
                promiseAggregator.setFailure(t);
                promiseAggregator.doneAllocatingPromises();
            }
            return promiseAggregator;
        }
        try {
            ctx.write(payload, promiseAggregator.newPromise());
        } catch (Throwable t) {
            promiseAggregator.setFailure(t);
        }
        return promiseAggregator.doneAllocatingPromises();
    }

    private ChannelFuture writeHeadersInternal(ChannelHandlerContext ctx,
                                               int streamId, Http2Headers headers, int padding, boolean endStream,
                                               boolean hasPriority, int streamDependency, short weight, boolean exclusive, ChannelPromise promise) {
        ByteBuf headerBlock = null;
        SimpleChannelPromiseAggregator promiseAggregator =
                new SimpleChannelPromiseAggregator(promise, ctx.channel(), ctx.executor());
        try {
          /*  verifyStreamId(streamId, STREAM_ID);
            if (hasPriority) {
                verifyStreamOrConnectionId(streamDependency, STREAM_DEPENDENCY);
                verifyPadding(padding);
                verifyWeight(weight);
            }*/

            // Encode the entire header block.
            headerBlock = ctx.alloc().buffer();
            //headersEncoder.encodeHeaders(streamId, headers, headerBlock);

            Http2Flags flags =
                    new Http2Flags().endOfStream(endStream).priorityPresent(hasPriority).paddingPresent(padding > 0);

            // Read the first fragment (possibly everything).
            int nonFragmentBytes = padding + flags.getNumPriorityBytes();
            int maxFragmentLength =  nonFragmentBytes;
            ByteBuf fragment = headerBlock.readRetainedSlice(min(headerBlock.readableBytes(), maxFragmentLength));

            // Set the end of headers flag for the first frame.
            flags.endOfHeaders(!headerBlock.isReadable());

            int payloadLength = fragment.readableBytes() + nonFragmentBytes;
            ByteBuf buf = ctx.alloc().buffer(HEADERS_FRAME_HEADER_LENGTH);
            writeFrameHeaderInternal(buf, payloadLength, HEADERS, flags, streamId);
            //writePaddingLength(buf, padding);

            if (hasPriority) {
                buf.writeInt(exclusive ? (int) (0x80000000L | streamDependency) : streamDependency);

                // Adjust the weight so that it fits into a single byte on the wire.
                buf.writeByte(weight - 1);
            }
            ctx.write(buf, promiseAggregator.newPromise());

            // Write the first fragment.
            ctx.write(fragment, promiseAggregator.newPromise());

            // Write out the padding, if any.
          /*  if (paddingBytes(padding) > 0) {
                ctx.write(ZERO_BUFFER.slice(0, paddingBytes(padding)), promiseAggregator.newPromise());
            }

            if (!flags.endOfHeaders()) {
                writeContinuationFrames(ctx, streamId, headerBlock, padding, promiseAggregator);
            }*/
        } catch (Exception e) {
            promiseAggregator.setFailure(e);
        } catch (Throwable t) {
            promiseAggregator.setFailure(t);
            promiseAggregator.doneAllocatingPromises();
            PlatformDependent.throwException(t);
        } finally {
            if (headerBlock != null) {
                headerBlock.release();
            }
        }
        return promiseAggregator.doneAllocatingPromises();
    }


    static final class SimpleChannelPromiseAggregator extends DefaultChannelPromise {
        private final ChannelPromise promise;
        private int expectedCount;
        private int doneCount;
        private Throwable lastFailure;
        private boolean doneAllocating;

        SimpleChannelPromiseAggregator(ChannelPromise promise, Channel c, EventExecutor e) {
            super(c, e);
            assert promise != null && !promise.isDone();
            this.promise = promise;
        }

        /**
         * Allocate a new promise which will be used to aggregate the overall success of this promise aggregator.
         * @return A new promise which will be aggregated.
         * {@code null} if {@link #doneAllocatingPromises()} was previously called.
         */
        public ChannelPromise newPromise() {
            assert !doneAllocating : "Done allocating. No more promises can be allocated.";
            ++expectedCount;
            return this;
        }

        /**
         * Signify that no more {@link #newPromise()} allocations will be made.
         * The aggregation can not be successful until this method is called.
         * @return The promise that is the aggregation of all promises allocated with {@link #newPromise()}.
         */
        public ChannelPromise doneAllocatingPromises() {
            if (!doneAllocating) {
                doneAllocating = true;
                if (doneCount == expectedCount || expectedCount == 0) {
                    return setPromise();
                }
            }
            return this;
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            if (allowFailure()) {
                ++doneCount;
                lastFailure = cause;
                if (allPromisesDone()) {
                    return tryPromise();
                }
                // TODO: We break the interface a bit here.
                // Multiple failure events can be processed without issue because this is an aggregation.
                return true;
            }
            return false;
        }

        /**
         * Fail this object if it has not already been failed.
         * <p>
         * This method will NOT throw an {@link IllegalStateException} if called multiple times
         * because that may be expected.
         */
        @Override
        public ChannelPromise setFailure(Throwable cause) {
            if (allowFailure()) {
                ++doneCount;
                lastFailure = cause;
                if (allPromisesDone()) {
                    return setPromise();
                }
            }
            return this;
        }

        @Override
        public ChannelPromise setSuccess(Void result) {
            if (awaitingPromises()) {
                ++doneCount;
                if (allPromisesDone()) {
                    setPromise();
                }
            }
            return this;
        }

        @Override
        public boolean trySuccess(Void result) {
            if (awaitingPromises()) {
                ++doneCount;
                if (allPromisesDone()) {
                    return tryPromise();
                }
                // TODO: We break the interface a bit here.
                // Multiple success events can be processed without issue because this is an aggregation.
                return true;
            }
            return false;
        }

        private boolean allowFailure() {
            return awaitingPromises() || expectedCount == 0;
        }

        private boolean awaitingPromises() {
            return doneCount < expectedCount;
        }

        private boolean allPromisesDone() {
            return doneCount == expectedCount && doneAllocating;
        }

        private ChannelPromise setPromise() {
            if (lastFailure == null) {
                promise.setSuccess();
                return super.setSuccess(null);
            } else {
                promise.setFailure(lastFailure);
                return super.setFailure(lastFailure);
            }
        }

        private boolean tryPromise() {
            if (lastFailure == null) {
                promise.trySuccess();
                return super.trySuccess(null);
            } else {
                promise.tryFailure(lastFailure);
                return super.tryFailure(lastFailure);
            }
        }
    }
}
