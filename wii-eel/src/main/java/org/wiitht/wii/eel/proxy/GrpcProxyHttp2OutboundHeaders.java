package org.wiitht.wii.eel.proxy;

import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * @Author tanghong
 * @Date 18-8-15-下午4:16
 * @Version 1.0
 */
public class GrpcProxyHttp2OutboundHeaders extends AbstractProxyHttp2Headers {

    private final AsciiString[] normalHeaders;
    private final AsciiString[] preHeaders;
    private static final AsciiString[] EMPTY = new AsciiString[]{};

    static GrpcProxyHttp2OutboundHeaders clientRequestHeaders(byte[][] serializedMetadata,
                                                         AsciiString authority, AsciiString path, AsciiString method, AsciiString scheme,
                                                         AsciiString userAgent) {
        AsciiString[] preHeaders = new AsciiString[] {
                Http2Headers.PseudoHeaderName.AUTHORITY.value(), authority,
                Http2Headers.PseudoHeaderName.PATH.value(), path,
                Http2Headers.PseudoHeaderName.METHOD.value(), method,
                Http2Headers.PseudoHeaderName.SCHEME.value(), scheme,
                Utils.CONTENT_TYPE_HEADER, Utils.CONTENT_TYPE_GRPC,
                Utils.TE_HEADER, Utils.TE_TRAILERS,
                Utils.USER_AGENT, userAgent,
        };
        return new GrpcProxyHttp2OutboundHeaders(preHeaders, serializedMetadata);
    }

    static GrpcProxyHttp2OutboundHeaders serverResponseHeaders(byte[][] serializedMetadata) {
        AsciiString[] preHeaders = new AsciiString[] {
                Http2Headers.PseudoHeaderName.STATUS.value(), Utils.STATUS_OK,
                Utils.CONTENT_TYPE_HEADER, Utils.CONTENT_TYPE_GRPC,
        };
        return new GrpcProxyHttp2OutboundHeaders(preHeaders, serializedMetadata);
    }

    static GrpcProxyHttp2OutboundHeaders serverResponseTrailers(byte[][] serializedMetadata) {
        return new GrpcProxyHttp2OutboundHeaders(EMPTY, serializedMetadata);
    }

    private GrpcProxyHttp2OutboundHeaders(AsciiString[] preHeaders, byte[][] serializedMetadata) {
        normalHeaders = new AsciiString[serializedMetadata.length];
        for (int i = 0; i < normalHeaders.length; i++) {
            normalHeaders[i] = new AsciiString(serializedMetadata[i], false);
        }
        this.preHeaders = preHeaders;
    }

    @Override
    @SuppressWarnings("ReferenceEquality") // STATUS.value() never changes.
    public CharSequence status() {
        // preHeaders is never null.  It has status as the first element or not at all.
        if (preHeaders.length >= 2 && preHeaders[0] == Http2Headers.PseudoHeaderName.STATUS.value()) {
            return preHeaders[1];
        }
        return null;
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iterator() {
        return new GrpcProxyHttp2OutboundHeaders.Itr();
    }

    @Override
    public int size() {
        return (normalHeaders.length + preHeaders.length) / 2;
    }

    private class Itr implements Map.Entry<CharSequence, CharSequence>,
            Iterator<Map.Entry<CharSequence, CharSequence>> {
        private int idx;
        private AsciiString[] current = preHeaders.length != 0 ? preHeaders : normalHeaders;
        private AsciiString key;
        private AsciiString value;

        @Override
        public boolean hasNext() {
            return idx < current.length;
        }

        /**
         * This function is ordered specifically to get ideal performance on OpenJDK.  If you decide to
         * change it, even in ways that don't seem possible to affect performance, please benchmark
         * speeds before and after.
         */
        @Override
        public Map.Entry<CharSequence, CharSequence> next() {
            if (hasNext()) {
                key = current[idx];
                value = current[idx + 1];
                idx += 2;
                if (idx >= current.length && current == preHeaders) {
                    current = normalHeaders;
                    idx = 0;
                }
                return this;
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public CharSequence getKey() {
            return key;
        }

        @Override
        public CharSequence getValue() {
            return value;
        }

        @Override
        public CharSequence setValue(CharSequence value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName()).append('[');
        String separator = "";
        for (Map.Entry<CharSequence, CharSequence> e : this) {
            CharSequence name = e.getKey();
            CharSequence value = e.getValue();
            builder.append(separator);
            builder.append(name).append(": ").append(value);
            separator = ", ";
        }
        builder.append(']');
        return builder.toString();
    }
}
