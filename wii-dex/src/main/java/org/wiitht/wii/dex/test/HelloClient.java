package org.wiitht.wii.dex.test;

import org.wii.dex.helloworld.GreeterGrpc;
import org.wii.dex.helloworld.HelloReply;
import org.wii.dex.helloworld.HelloRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wiitht.wii.dex.mesh.client.ClientFactoryBuilder;

import java.util.concurrent.TimeUnit;

/**
 * @Author tanghong
 * @Date 18-8-14-下午2:23
 * @Version 1.0
 */
public class HelloClient {


    private final ManagedChannel channel;
    private final GreeterGrpc.GreeterBlockingStub blockingStub;

    private static final Logger logger = LoggerFactory.getLogger(HelloClient.class);

    /** Construct client connecting to HelloWorld server at {@code host:port}. */
    public HelloClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                // needing certificates.
                .usePlaintext()
                .build());
    }

    /** Construct client for accessing HelloWorld server using the existing channel. */
    HelloClient(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = GreeterGrpc.newBlockingStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /** Say hello to server. */
    public void greet(String name) {
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        HelloReply response;
        try {
            response = blockingStub.sayHello(request);
        } catch (StatusRuntimeException e) {
            logger.info("RPC failed: {0}", e.getStatus());
            return;
        }
        System.out.println(response.getMessage());
        //logger.info("Greeting: " + response.getMessage());
    }

    /**
     * Greet server. If provided, the first element of {@code args} is the name to use in the
     * greeting.
     */
    public static void main(String[] args) throws Exception {
        HelloClient client = new HelloClient("192.168.8.130", 9001);
        try {
            client.greet("1111");
        } finally {
            client.shutdown();
        }

       /* GreeterGrpc.GreeterBlockingStub blockingStub = new ClientFactoryBuilder().build().newClient("gproto+http://127.0.0.1:8083/",GreeterGrpc.GreeterBlockingStub.class);
        HelloRequest request = HelloRequest.newBuilder().setName("111111").build();
        HelloReply reply = blockingStub.sayHello(request);
        System.out.println(reply.getMessage());*/


    }
}
