package com.docstorage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docstorage.entity.DocAccessLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocAccessLogMapper extends BaseMapper<DocAccessLog> {
}