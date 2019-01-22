package org.wiitht.wii.dex.proxy;

import io.grpc.internal.WritableBuffer;
import io.grpc.internal.WritableBufferAllocator;
import io.netty.buffer.ByteBufAllocator;

/**
 * @Author tanghong
 * @Date 18-8-15-下午4:29
 * @Version 1.0
 */
public class NettyProxyWritableBufferAllocator implements WritableBufferAllocator {

    // Use 4k as our minimum buffer size.
    private static final int MIN_BUFFER = 4096;

    // Set the maximum buffer size to 1MB
    private static final int MAX_BUFFER = 1024 * 1024;

    private final ByteBufAllocator allocator;

    NettyProxyWritableBufferAllocator(ByteBufAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public WritableBuffer allocate(int capacityHint) {
        capacityHint = Math.min(MAX_BUFFER, Math.max(MIN_BUFFER, capacityHint));
        return new NettyProxyWritableBuffer(allocator.buffer(capacityHint, capacityHint));
    }
}
