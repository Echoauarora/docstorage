package com.docstorage.util;



import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * MD5 工具类
 * <p>
 * 面试考点 - 大文件MD5计算的取舍:
 * 方案一: 前端分片计算各自MD5 → 后端合并时再计算整体MD5 → 安全但前端有计算压力
 * 方案二: 前端整体计算MD5再上传 → 简单但内存占用大(文件>2GB可能OOM)
 * 方案三(推荐): 前端按块流式计算MD5(如SparkMD5), 内存友好
 * <p>
 * 后端兜底: 合并完成后也计算一次MD5与前端传值比对, 防篡改
 */
public class Md5Util {

    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    /**
     * 计算文件MD5 (流式读取, 支持大文件)
     * - 使用 8KB buffer, 避免一次性加载整个文件到内存
     * - 面试点: bufferSize 不是越大越好, 大文件建议 8KB~64KB
     */
    public static String calculateFileMd5(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return calculateStreamMd5(is);
        }
    }

    /**
     * 计算流MD5 (通用入口)
     */
    public static String calculateStreamMd5(InputStream is) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192]; // 8KB buffer
            int len;
            while ((len = is.read(buffer)) != -1) {
                md.update(buffer, 0, len);
            }
            return bytesToHex(md.digest());
        } catch (Exception e) {
            throw new IOException("MD5计算失败", e);
        }
    }

    /**
     * 计算字节数组MD5
     */
    public static String calculateBytesMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return bytesToHex(md.digest(data));
        } catch (Exception e) {
            throw new RuntimeException("MD5计算失败", e);
        }
    }

    /**
     * 字节数组转16进制字符串 (大写)
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            sb.append(HEX_CHARS[v >>> 4]);
            sb.append(HEX_CHARS[v & 0x0F]);
        }
        return sb.toString();
    }
}