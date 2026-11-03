package com.docstorage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分片上传记录实体
 */
@Data
@TableName("doc_upload_part")
public class DocUploadPart {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分片上传uploadId */
    private String uploadId;

    private String fileCode;

    /** 分片序号 (从1开始) */
    private Integer partNumber;

    private Long partSize;

    private String partMd5;

    /** 分片ETag (校验标识) */
    private String etag;

    /** 状态: 1-已上传, 2-已合并 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}