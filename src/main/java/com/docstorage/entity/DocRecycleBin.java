package com.docstorage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站实体
 */
@Data
@TableName("doc_recycle_bin")
public class DocRecycleBin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fileId;

    private String fileCode;

    private String filename;

    private Long fileSize;

    private Long ownerId;

    private Long deleteUserId;

    private LocalDateTime expireTime;

    private String deleteReason;

    /** 状态: 1-回收站中, 2-已恢复, 3-已永久删除 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}