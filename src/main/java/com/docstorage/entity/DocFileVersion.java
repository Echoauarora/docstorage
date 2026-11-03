package com.docstorage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件版本实体
 */
@Data
@TableName("doc_file_version")
public class DocFileVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fileId;

    /** 版本号 */
    private Integer versionNumber;

    private Long fileSize;

    private String fileMd5;

    private String storagePath;

    private String bucketName;

    private String changeLog;

    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}