package com.docstorage.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 存储策略工厂
 * <p>
 * 当前基于 LocalFileStorageStrategy 本地磁盘分片存储实现
 * 架构预留扩展点: 通过实现 StorageStrategy 接口, 可无缝切换至对象存储 (MinIO / OSS / S3)
 * <p>
 * 面试考点: 策略模式 + 工厂模式, 运行时可切换存储后端
 */
@Slf4j
@Component
public class StorageStrategyFactory {

    private final StorageStrategy storageStrategy;

    public StorageStrategyFactory(StorageStrategy storageStrategy) {
        this.storageStrategy = storageStrategy;
        log.info("Storage strategy initialized: {}", storageStrategy.getClass().getSimpleName());
    }

    public StorageStrategy getStrategy() {
        return storageStrategy;
    }
}