package org.wiitht.wii.eel.trail;

import io.grpc.Metadata;
import io.grpc.Status;
import io.netty.buffer.*;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.internal.PlatformDependent;
import org.wiitht.wii.eel.proxy.SendDataCommand;
import org.wiitht.wii.eel.proxy.SendProxyFrameCommand;
import org.wiitht.wii.eel.proxy.SendProxyHeaderCommand;
import org.wiitht.wii.eel.proxy.WriteQueue;

import java.nio.ByteBuffer;

/**
 * @Author tanghong
 * @Date 18-8-21-下午6:28
 * @Version 1.0
 */
public class GrpcProxyListener {

    private static final int MIN_BUFFER = 4096;

    // Set the maximum buffer size to 1MB
    private static final int MAX_BUFFER = 1024 * 1024;
    private WriteQueue queue;

    public GrpcProxyListener(){
    }

    public GrpcProxyListener(WriteQueue queue){
        this.queue = queue;
    }

    public void onData(ByteBuf byteBuf){
        queue.enqueue(new SendDataCommand(byteBuf.copy()), true);
    }

    public void onHeaders(int streamId, Http2Headers headers) {
        queue.enqueue(SendProxyHeaderCommand.createHeaders(new StreamIdHolder() {
            @Override
            public int id() {
                return streamId;
            }
        }, headers), true);
    }

    public void onMessage(int streamId, ByteBuf value) {
        int windex = value.writerIndex();
       // ByteBuf byteBuf = Unpooled.directBuffer();
       // byteBuf.writeBytes(ByteBufUtil.getBytes(value));


        byte[] headerScratch = new byte[5];
        final byte UNCOMPRESSED = 0;
        ByteBuffer header = ByteBuffer.wrap(headerScratch);
        header.put(UNCOMPRESSED);
        header.putInt(12);
        int capacityHint = Math.min(MAX_BUFFER, Math.max(MIN_BUFFER, windex));
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(PlatformDependent.directBufferPreferred());
        ByteBuf content = allocator.buffer(capacityHint, capacityHint);
        //content.writeBytes(headerScratch, 0, header.position());
        content.writeBytes(ByteBufUtil.getBytes(value));

        queue.enqueue(new SendProxyFrameCommand(new StreamIdHolder() {
            @Override
            public int id() {
                return streamId;
            }
        }, content, false), true);
    }

    public void onClose(Status status, Metadata trailers) {

    }
}
