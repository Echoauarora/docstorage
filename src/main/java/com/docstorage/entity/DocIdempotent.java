package com.docstorage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等控制实体
 */
@Data
@TableName("doc_idempotent")
public class DocIdempotent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等键 (业务编号) */
    private String idempotentKey;

    private String module;

    /** 首次执行结果JSON, 重复请求直接返回 */
    private String resultData;

    /** 状态: 1-处理中, 2-已完成 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime expireTime;
}