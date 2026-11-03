package com.docstorage.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 存储配置属性
 * <p>
 * 当前采用本地磁盘分片存储, 架构预留 StorageStrategy 接口, 后续可扩展对接对象存储
 */
@Data
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /** 默认分片大小 (字节), 默认 5MB */
    private long defaultPartSize = 5242880L;

    /** 本地磁盘存储配置 */
    private Local local = new Local();

    @Data
    public static class Local {
        /** 正式文件存储根目录 */
        private String rootPath = "./docstorage-data/files";
        /** 分片临时文件目录 (与正式文件分开, 防脏数据) */
        private String tempPath = "./docstorage-data/temp";
        /** 分片文件命名模板 (uploadId + partNumber 组成唯一标识) */
        private String partSuffix = ".part";
    }
}