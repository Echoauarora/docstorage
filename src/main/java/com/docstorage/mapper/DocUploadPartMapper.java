package com.docstorage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docstorage.entity.DocUploadPart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocUploadPartMapper extends BaseMapper<DocUploadPart> {

    /**
     * 查询 uploadId 下所有已上传的分片 (按序号排序)
     * 用于合并分片时校验完整性和按序合并
     */
    List<DocUploadPart> selectByUploadIdOrderByPartNumber(@Param("uploadId") String uploadId);
}