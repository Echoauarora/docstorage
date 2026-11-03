package com.docstorage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docstorage.entity.DocFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocFileMapper extends BaseMapper<DocFile> {

    /**
     * 根据MD5查询已存在的正常文件 (秒传核心)
     * 只返回已合并完成的文件, 避免引用上传中的中间产物
     */
    @Select("SELECT * FROM doc_file WHERE file_md5 = #{md5} AND file_status = 2 AND deleted = 0 LIMIT 1")
    DocFile selectExistingByMd5(@Param("md5") String md5);
}