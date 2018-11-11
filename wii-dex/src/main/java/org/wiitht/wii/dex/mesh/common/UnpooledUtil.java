package org.wiitht.wii.dex.mesh.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.UnsupportedEncodingException;

/**
 * @Author tanghong
 * @Date 18-10-23-下午6:11
 * @Version 1.0
 */
public class UnpooledUtil {

    public static ByteBuf write(String str){
        try {
            return Unpooled.copiedBuffer(str.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
