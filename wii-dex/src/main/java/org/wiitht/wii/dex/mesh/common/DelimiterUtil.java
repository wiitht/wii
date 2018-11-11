package org.wiitht.wii.dex.mesh.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * @Author tanghong
 * @Date 18-10-23-下午6:08
 * @Version 1.0
 */
public class DelimiterUtil {

    public static ByteBuf getDefault(){
        try {
            return Unpooled.copiedBuffer("1234".getBytes("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
