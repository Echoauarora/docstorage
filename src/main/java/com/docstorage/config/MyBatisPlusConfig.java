package com.docstorage.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置类
 * <p>
 * 配置内容:
 * 1. 分页插件 (MySQL8方言)
 * 2. 乐观锁插件 (version字段)
 * 3. 自动填充 create_time / update_time
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器配置
     * - PaginationInnerInterceptor: 物理分页, 防止SQL注入
     * - OptimisticLockerInnerInterceptor: 乐观锁, 配合 @Version 注解
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // MySQL 8.0 分页
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(1000L); // 单次查询上限 1000 条
        interceptor.addInnerInterceptor(pagination);
        // 乐观锁
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 自动填充处理器
     * - 插入时自动填充 create_time / update_time
     * - 更新时自动填充 update_time
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}