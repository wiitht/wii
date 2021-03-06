package org.wiitht.wii.dex.mesh.proxy;

import com.google.common.io.BaseEncoding;
import io.grpc.Metadata;
import io.grpc.internal.GrpcUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.netty.util.AsciiString;
import io.netty.util.internal.PlatformDependent;
import org.wiitht.wii.dex.proxy.WriteQueue;
import org.wiitht.wii.dex.trail.GrpcProxyClient;
import org.wiitht.wii.dex.trail.SendFrameCommand;
import org.wiitht.wii.dex.trail.SendHeaderCommand;
import org.wiitht.wii.dex.trail.StreamIdHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Charsets.US_ASCII;
import static com.google.common.base.Preconditions.checkArgument;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.AsciiString.isUpperCase;

public class TestHandlerFactory {

    private static final int DEFAULT_FLOW_CONTROL_WINDOW = 1048576; // 1MiB
    private static final AsciiString TE_HEADER = AsciiString.of(GrpcUtil.TE_HEADER.name());
    private static int maxHeaderListSize = GrpcUtil.DEFAULT_MAX_HEADER_LIST_SIZE;

    public static TestProxyConnectionHandler createHandler(){
        Http2FrameLogger frameLogger = new Http2FrameLogger(LogLevel.DEBUG, TestProxyConnectionHandler.class);
        Http2Connection connection = new DefaultHttp2Connection(true);

        Http2HeadersDecoder headersDecoder = new GrpcHttp2ServerHeadersDecoder(maxHeaderListSize);


       /* Http2FrameReader baseFrameReader = new Http2InboundFrameLogger(
                new TestProxyHttp2FrameReader(headersDecoder), frameLogger);
        Http2FrameWriter baseFrameWriter =
                new Http2OutboundFrameLogger(new DefaultHttp2FrameWriter(), frameLogger);


        WeightedFairQueueByteDistributor dist = new WeightedFairQueueByteDistributor(connection);
        dist.allocationQuantum(16 * 1024); // Make benchmarks fast again.
        DefaultHttp2RemoteFlowController controller =
                new DefaultHttp2RemoteFlowController(connection, dist);
        connection.remote().flowController(controller);
        final KeepAliveEnforcer keepAliveEnforcer = new KeepAliveEnforcer(
                false, TimeUnit.MINUTES.toNanos(5), TimeUnit.NANOSECONDS);

        // Create the local flow controller configured to auto-refill the connection window.
        connection.local().flowController(
                new DefaultHttp2LocalFlowController(connection, DEFAULT_WINDOW_UPDATE_RATIO, true));
        WriteMonitoringFrameWriter frameWriter = new WriteMonitoringFrameWriter(baseFrameWriter, keepAliveEnforcer);
        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder,
                baseFrameReader);*/

        Http2FrameWriter frameWriter = new Http2OutboundFrameLogger(new DefaultHttp2FrameWriter(), frameLogger);
        Http2FrameReader frameReader = new Http2InboundFrameLogger(new TestProxyHttp2FrameReader(), frameLogger);
        DefaultHttp2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        DefaultHttp2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, frameReader);

        decoder.frameListener(new TestHandlerFactory.FrameListener());

        Http2Settings http2Settings = new Http2Settings();
        http2Settings.initialWindowSize(DEFAULT_FLOW_CONTROL_WINDOW);
        http2Settings.maxConcurrentStreams(Integer.MAX_VALUE);
        http2Settings.maxHeaderListSize(GrpcUtil.DEFAULT_MAX_HEADER_LIST_SIZE);
        return new TestProxyConnectionHandler(decoder, encoder, http2Settings);
    }


    private static class WriteMonitoringFrameWriter extends DecoratingHttp2FrameWriter {
        private final KeepAliveEnforcer keepAliveEnforcer;

        public WriteMonitoringFrameWriter(Http2FrameWriter delegate,
                                          KeepAliveEnforcer keepAliveEnforcer) {
            super(delegate);
            this.keepAliveEnforcer = keepAliveEnforcer;
        }

        @Override
        public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                       int padding, boolean endStream, ChannelPromise promise) {
            keepAliveEnforcer.resetCounters();
            return super.writeData(ctx, streamId, data, padding, endStream, promise);
        }

        @Override
        public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                          int padding, boolean endStream, ChannelPromise promise) {
            keepAliveEnforcer.resetCounters();
            return super.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
        }

        @Override
        public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                          int streamDependency, short weight, boolean exclusive, int padding, boolean endStream,
                                          ChannelPromise promise) {
            keepAliveEnforcer.resetCounters();
            return super.writeHeaders(ctx, streamId, headers, streamDependency, weight, exclusive,
                    padding, endStream, promise);
        }
    }

    static final class GrpcHttp2ServerHeadersDecoder extends DefaultHttp2HeadersDecoder {

        GrpcHttp2ServerHeadersDecoder(long maxHeaderListSize) {
            super(true, maxHeaderListSize);
        }

        @Override
        protected GrpcHttp2InboundHeaders newHeaders() {
            return new GrpcHttp2RequestHeaders(numberOfHeadersGuess());
        }
    }


    public static class FrameListener extends Http2FrameAdapter {
        private boolean firstSettings = true;
        private WriteQueue serverWriteQueue;

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
           /* if (firstSettings) {
                firstSettings = false;
                // Delay transportReady until we see the client's HTTP handshake, for coverage with
                // handshakeTimeout
                attributes = transportListener.transportReady(negotiationAttributes);
            }*/
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                              boolean endOfStream) throws Http2Exception {

            SendFrameCommand frameCommand = new SendFrameCommand(new StreamIdHolder() {
                @Override
                public int id() {
                    return streamId;
                }
            }, data, endOfStream);
            GrpcProxyClient.sendFrame(frameCommand);
            /*if (keepAliveManager != null) {
                keepAliveManager.onDataReceived();
            }*/
            //NettyProxyHandler.this.onDataRead(streamId, data, padding, endOfStream);
            return padding;
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx,
                                  int streamId,
                                  Http2Headers headers,
                                  int streamDependency,
                                  short weight,
                                  boolean exclusive,
                                  int padding,
                                  boolean endStream) throws Http2Exception {
            serverWriteQueue = new WriteQueue(ctx.channel());
            // ????????????????????????
            GrpcProxyClient.start(serverWriteQueue);
            SendHeaderCommand command = SendHeaderCommand.createHeaders(new StreamIdHolder() {
                @Override
                public int id() {
                    return streamId;
                }
            }, headers);
            GrpcProxyClient.sendHeader(command);
           /* if (keepAliveManager != null) {
                keepAliveManager.onDataReceived();
            }*/
            //NettyProxyHandler.this.onHeadersRead(ctx, streamId, headers);
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                throws Http2Exception {
           /* if (keepAliveManager != null) {
                keepAliveManager.onDataReceived();
            }
            NettyProxyHandler.this.onRstStreamRead(streamId, errorCode);*/
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
        }
    }


    static final class GrpcHttp2RequestHeaders extends GrpcHttp2InboundHeaders {

        private static final AsciiString PATH_HEADER = AsciiString.of(":path");
        private static final AsciiString AUTHORITY_HEADER = AsciiString.of(":authority");
        private static final AsciiString METHOD_HEADER = AsciiString.of(":method");
        private static final AsciiString SCHEME_HEADER = AsciiString.of(":scheme");

        private AsciiString path;
        private AsciiString authority;
        private AsciiString method;
        private AsciiString scheme;
        private AsciiString te;

        GrpcHttp2RequestHeaders(int numHeadersGuess) {
            super(numHeadersGuess);
        }

        @Override
        public Http2Headers add(CharSequence csName, CharSequence csValue) {
            AsciiString name = validateName(requireAsciiString(csName));
            AsciiString value = requireAsciiString(csValue);
            if (isPseudoHeader(name)) {
                addPseudoHeader(name, value);
                return this;
            }
            if (equals(TE_HEADER, name)) {
                te = value;
                return this;
            }
            return add(name, value);
        }

        @Override
        public CharSequence get(CharSequence csName) {
            AsciiString name = requireAsciiString(csName);
            checkArgument(!isPseudoHeader(name), "Use direct accessor methods for pseudo headers.");
            if (equals(TE_HEADER, name)) {
                return te;
            }
            return get(name);
        }

        private void addPseudoHeader(CharSequence csName, CharSequence csValue) {
            AsciiString name = requireAsciiString(csName);
            AsciiString value = requireAsciiString(csValue);

            if (equals(PATH_HEADER, name)) {
                path = value;
            } else if (equals(AUTHORITY_HEADER, name)) {
                authority = value;
            } else if (equals(METHOD_HEADER, name)) {
                method = value;
            } else if (equals(SCHEME_HEADER, name)) {
                scheme = value;
            } else {
                PlatformDependent.throwException(
                        connectionError(PROTOCOL_ERROR, "Illegal pseudo-header '%s' in request.", name));
            }
        }

        @Override
        public CharSequence path() {
            return path;
        }

        @Override
        public CharSequence authority() {
            return authority;
        }

        @Override
        public CharSequence method() {
            return method;
        }

        @Override
        public CharSequence scheme() {
            return scheme;
        }

        /**
         * This method is called in tests only.
         */
        @Override
        public List<CharSequence> getAll(CharSequence csName) {
            AsciiString name = requireAsciiString(csName);
            if (isPseudoHeader(name)) {
                // This code should never be reached.
                throw new IllegalArgumentException("Use direct accessor methods for pseudo headers.");
            }
            if (equals(TE_HEADER, name)) {
                return Collections.singletonList((CharSequence) te);
            }
            return super.getAll(csName);
        }

        /**
         * This method is called in tests only.
         */
        @Override
        public int size() {
            int size = 0;
            if (path != null) {
                size++;
            }
            if (authority != null) {
                size++;
            }
            if (method != null) {
                size++;
            }
            if (scheme != null) {
                size++;
            }
            if (te != null) {
                size++;
            }
            size += super.size();
            return size;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(getClass().getSimpleName()).append('[');
            boolean prependSeparator = false;

            if (path != null) {
                appendNameAndValue(builder, PATH_HEADER, path, prependSeparator);
                prependSeparator = true;
            }
            if (authority != null) {
                appendNameAndValue(builder, AUTHORITY_HEADER, authority, prependSeparator);
                prependSeparator = true;
            }
            if (method != null) {
                appendNameAndValue(builder, METHOD_HEADER, method, prependSeparator);
                prependSeparator = true;
            }
            if (scheme != null) {
                appendNameAndValue(builder, SCHEME_HEADER, scheme, prependSeparator);
                prependSeparator = true;
            }
            if (te != null) {
                appendNameAndValue(builder, TE_HEADER, te, prependSeparator);
            }

            String namesAndValues = namesAndValuesToString();

            if (builder.length() > 0 && namesAndValues.length() > 0) {
                builder.append(", ");
            }

            builder.append(namesAndValues);
            builder.append(']');

            return builder.toString();
        }
    }

    abstract static class GrpcHttp2InboundHeaders extends AbstractHttp2Headers {

        private static final AsciiString binaryHeaderSuffix =
                new AsciiString(Metadata.BINARY_HEADER_SUFFIX.getBytes(US_ASCII));

        private byte[][] namesAndValues;
        private AsciiString[] values;
        private int namesAndValuesIdx;

        GrpcHttp2InboundHeaders(int numHeadersGuess) {
            checkArgument(numHeadersGuess > 0, "numHeadersGuess needs to be gt zero.");
            namesAndValues = new byte[numHeadersGuess * 2][];
            values = new AsciiString[numHeadersGuess];
        }

        protected Http2Headers add(AsciiString name, AsciiString value) {
            if (namesAndValuesIdx == namesAndValues.length) {
                expandHeadersAndValues();
            }
            byte[] nameBytes = bytes(name);
            byte[] valueBytes = toBinaryValue(name, value);
            values[namesAndValuesIdx / 2] = value;
            namesAndValues[namesAndValuesIdx] = nameBytes;
            namesAndValuesIdx++;
            namesAndValues[namesAndValuesIdx] = valueBytes;
            namesAndValuesIdx++;
            return this;
        }

        protected CharSequence get(AsciiString name) {
            for (int i = 0; i < namesAndValuesIdx; i += 2) {
                if (equals(name, namesAndValues[i])) {
                    return values[i / 2];
                }
            }
            return null;
        }

        @Override
        public CharSequence status() {
            return get(Http2Headers.PseudoHeaderName.STATUS.value());
        }

        @Override
        public List<CharSequence> getAll(CharSequence csName) {
            AsciiString name = requireAsciiString(csName);
            List<CharSequence> returnValues = new ArrayList<CharSequence>(4);
            for (int i = 0; i < namesAndValuesIdx; i += 2) {
                if (equals(name, namesAndValues[i])) {
                    returnValues.add(values[i / 2]);
                }
            }
            return returnValues;
        }

        /**
         * Returns the header names and values as bytes. An even numbered index contains the
         * {@code byte[]} representation of a header name (in insertion order), and the subsequent
         * odd index number contains the corresponding header value.
         *
         * <p>The values of binary headers (with a -bin suffix), are already base64 decoded.
         *
         * <p>The array may contain several {@code null} values at the end. A {@code null} value an
         * index means that all higher numbered indices also contain {@code null} values.
         */
        byte[][] namesAndValues() {
            return namesAndValues;
        }

        /**
         * Returns the number of none-null headers in {@link #namesAndValues()}.
         */
        protected int numHeaders() {
            return namesAndValuesIdx / 2;
        }

        protected static boolean equals(AsciiString str0, byte[] str1) {
            return equals(str0.array(), str0.arrayOffset(), str0.length(), str1, 0, str1.length);
        }

        protected static boolean equals(AsciiString str0, AsciiString str1) {
            return equals(str0.array(), str0.arrayOffset(), str0.length(), str1.array(),
                    str1.arrayOffset(), str1.length());
        }

        protected static boolean equals(byte[] bytes0, int offset0, int length0, byte[] bytes1,
                                        int offset1, int length1) {
            if (length0 != length1) {
                return false;
            }
            return PlatformDependent.equals(bytes0, offset0, bytes1, offset1, length0);
        }

        @SuppressWarnings("BetaApi") // BaseEncoding is stable in Guava 20.0
        private static byte[] toBinaryValue(AsciiString name, AsciiString value) {
            return name.endsWith(binaryHeaderSuffix)
                    ? BaseEncoding.base64().decode(value)
                    : bytes(value);
        }

        protected static byte[] bytes(AsciiString str) {
            return str.isEntireArrayUsed() ? str.array() : str.toByteArray();
        }

        protected static AsciiString requireAsciiString(CharSequence cs) {
            if (!(cs instanceof AsciiString)) {
                throw new IllegalArgumentException("AsciiString expected. Was: " + cs.getClass().getName());
            }
            return (AsciiString) cs;
        }

        protected static boolean isPseudoHeader(AsciiString str) {
            return !str.isEmpty() && str.charAt(0) == ':';
        }

        protected AsciiString validateName(AsciiString str) {
            int offset = str.arrayOffset();
            int length = str.length();
            final byte[] data = str.array();
            for (int i = offset; i < offset + length; i++) {
                if (isUpperCase(data[i])) {
                    PlatformDependent.throwException(connectionError(PROTOCOL_ERROR,
                            "invalid header name '%s'", str));
                }
            }
            return str;
        }

        private void expandHeadersAndValues() {
            int newValuesLen = Math.max(2, values.length + values.length / 2);
            int newNamesAndValuesLen = newValuesLen * 2;

            byte[][] newNamesAndValues = new byte[newNamesAndValuesLen][];
            AsciiString[] newValues = new AsciiString[newValuesLen];
            System.arraycopy(namesAndValues, 0, newNamesAndValues, 0, namesAndValues.length);
            System.arraycopy(values, 0, newValues, 0, values.length);
            namesAndValues = newNamesAndValues;
            values = newValues;
        }

        @Override
        public int size() {
            return numHeaders();
        }

        protected static void appendNameAndValue(StringBuilder builder, CharSequence name,
                                                 CharSequence value, boolean prependSeparator) {
            if (prependSeparator) {
                builder.append(", ");
            }
            builder.append(name).append(": ").append(value);
        }

        protected final String namesAndValuesToString() {
            StringBuilder builder = new StringBuilder();
            boolean prependSeparator = false;
            for (int i = 0; i < namesAndValuesIdx; i += 2) {
                String name = new String(namesAndValues[i], US_ASCII);
                // If binary headers, the value is base64 encoded.
                AsciiString value = values[i / 2];
                appendNameAndValue(builder, name, value, prependSeparator);
                prependSeparator = true;
            }
            return builder.toString();
        }
    }

}
