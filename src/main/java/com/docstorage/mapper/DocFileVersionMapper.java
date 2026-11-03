package com.docstorage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docstorage.entity.DocFileVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocFileVersionMapper extends BaseMapper<DocFileVersion> {
}