package com.docstorage.util;

import cn.hutool.core.util.IdUtil;

/**
 * 文件存储路径生成器
 * <p>
 * 设计思路: 按年/月/日分目录, 避免单目录文件过多
 * 命名规则: {yyyy}/{MM}/{dd}/{UUID}.{ext}
 */
public class StoragePathUtil {

    public static String generate(String ext) {
        java.time.LocalDate now = java.time.LocalDate.now();
        String dir = now.getYear() + "/" +
                String.format("%02d", now.getMonthValue()) + "/" +
                String.format("%02d", now.getDayOfMonth());
        String uuid = IdUtil.fastSimpleUUID();
        return dir + "/" + uuid + (ext != null ? "." + ext : "");
    }

    /**
     * 版本存储路径 - 带版本号
     */
    public static String generateWithVersion(String ext, int version) {
        java.time.LocalDate now = java.time.LocalDate.now();
        String dir = now.getYear() + "/" +
                String.format("%02d", now.getMonthValue()) + "/" +
                String.format("%02d", now.getDayOfMonth());
        String uuid = IdUtil.fastSimpleUUID();
        return dir + "/v" + version + "_" + uuid + (ext != null ? "." + ext : "");
    }
}