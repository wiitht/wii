package org.wiitht.wii.dex.proxy;

import io.grpc.internal.WritableBuffer;
import io.netty.buffer.ByteBuf;

/**
 * @Author tanghong
 * @Date 18-8-15-下午4:29
 * @Version 1.0
 */
public class NettyProxyWritableBuffer implements WritableBuffer {

    private final ByteBuf bytebuf;

    NettyProxyWritableBuffer(ByteBuf bytebuf) {
        this.bytebuf = bytebuf;
    }

    @Override
    public void write(byte[] src, int srcIndex, int length) {
        bytebuf.writeBytes(src, srcIndex, length);
    }

    @Override
    public void write(byte b) {
        bytebuf.writeByte(b);
    }

    @Override
    public int writableBytes() {
        return bytebuf.writableBytes();
    }

    @Override
    public int readableBytes() {
        return bytebuf.readableBytes();
    }

    @Override
    public void release() {
        bytebuf.release();
    }

    ByteBuf bytebuf() {
        return bytebuf;
    }
}
