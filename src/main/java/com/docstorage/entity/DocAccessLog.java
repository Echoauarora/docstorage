package com.docstorage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访问日志实体
 */
@Data
@TableName("doc_access_log")
public class DocAccessLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private Long userId;

    private Long fileId;

    /** 操作类型: view, edit, delete, upload, download */
    private String accessType;

    private String accessIp;

    private String userAgent;

    private Integer duration;

    private Integer result;

    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}