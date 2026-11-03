package com.docstorage.config;

import com.docstorage.storage.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 存储模块配置
 * <p>
 * 启用 StorageProperties 配置绑定, 让 application.yml 中的 storage.* 生效
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {
}