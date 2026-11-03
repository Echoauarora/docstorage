package com.docstorage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元数据实体
 */
@Data
@TableName("doc_file")
public class DocFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件业务编码 (幂等唯一) */
    private String fileCode;

    private String filename;

    private String fileExt;

    private Long fileSize;

    /** 文件MD5 (秒传/去重用) */
    private String fileMd5;

    private String fileType;

    /** 存储路径 */
    private String storagePath;

    private String bucketName;

    /** 缩略图URL (异步生成) */
    private String thumbnailUrl;

    /** 分片上传uploadId */
    private String uploadId;

    private Long ownerId;

    private Long parentId;

    /** 文件状态: 1-正常, 2-上传中, 3-已删除 */
    private Integer fileStatus;

    private Integer version;

    private String shareCode;

    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}