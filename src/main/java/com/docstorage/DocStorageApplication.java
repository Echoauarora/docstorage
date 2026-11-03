package com.docstorage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 企业文档权限与分片存储系统 - 启动类
 * <p>
 * 核心功能:
 * 1. RBAC权限控制
 * 2. 本地磁盘分片上传 + 断点续传 (架构预留 StorageStrategy 接口, 可扩展对接对象存储)
 * 3. MD5秒传 + 文件去重
 * 4. 文档版本管理
 * 5. 回收站软删除
 * 6. Kafka异步任务 (缩略图、日志)
 * 7. 接口幂等控制
 * 8. Redis缓存策略
 */
@SpringBootApplication
@MapperScan("com.docstorage.mapper")
@EnableAsync
@EnableScheduling
public class DocStorageApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocStorageApplication.class, args);
    }
}