package org.wiitht.wii.dex.mesh.client;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:35
 * @Version 1.0
 */
public class ConvertUtils {
    static int safeLongToInt(long longValue) {
        return (int) Math.min(Math.max(longValue, Integer.MIN_VALUE), Integer.MAX_VALUE);
    }

    private ConvertUtils() {}
}
