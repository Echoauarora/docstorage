package com.docstorage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docstorage.entity.DocIdempotent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocIdempotentMapper extends BaseMapper<DocIdempotent> {

    /**
     * 根据幂等键查询 (已完成的请求可直接返回缓存结果)
     */
    @Select("SELECT * FROM doc_idempotent WHERE idempotent_key = #{key} AND expire_time > NOW() LIMIT 1")
    DocIdempotent selectValidByKey(@Param("key") String key);
}