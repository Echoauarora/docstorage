package com.docstorage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件权限实体
 */
@Data
@TableName("doc_file_permission")
public class DocFilePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fileId;

    private Long userId;

    private Long roleId;

    /** 权限类型: 1-查看, 2-编辑, 3-删除, 4-下载 */
    private Integer permType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}