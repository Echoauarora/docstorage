package com.docstorage.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设置文件权限请求
 */
@Data
public class SetFilePermissionReq {

    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    /** 用户ID (与roleId至少一个) */
    private Long userId;

    /** 角色ID */
    private Long roleId;

    /** 权限类型: 1-查看, 2-编辑, 3-删除, 4-下载 */
    @NotNull(message = "权限类型不能为空")
    private Integer permType;
}