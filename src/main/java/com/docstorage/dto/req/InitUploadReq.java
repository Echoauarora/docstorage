package com.docstorage.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 分片初始化请求
 */
@Data
public class InitUploadReq {

    /** 文件业务编码 (幂等唯一标识) */
    @NotBlank(message = "文件业务编码不能为空")
    private String fileCode;

    /** 原始文件名 */
    @NotBlank(message = "文件名不能为空")
    private String filename;

    /** 文件MD5 (秒传/去重核心) */
    @NotBlank(message = "文件MD5不能为空")
    private String fileMd5;

    /** 文件总大小 */
    @Positive(message = "文件大小必须大于0")
    private Long fileSize;

    /** 分片大小 (可选, 默认5MB) */
    private Long partSize;

    /** 父级文件夹ID */
    private Long parentId;

    /** 文件类型 */
    private String fileType;
}