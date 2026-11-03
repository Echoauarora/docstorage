package com.docstorage.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 版本回滚请求
 */
@Data
public class RollbackVersionReq {

    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    @NotNull(message = "目标版本号不能为空")
    private Integer targetVersion;

    private String changeLog;
}