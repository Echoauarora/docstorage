package com.docstorage.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 合并分片请求
 */
@Data
public class CompleteUploadReq {

    @NotBlank(message = "uploadId不能为空")
    private String uploadId;

    @NotBlank(message = "fileCode不能为空")
    private String fileCode;

    /**
     * 所有已上传分片的 ETag 列表 (合并必传)
     * ETag 格式: "{etag}" 带双引号
     */
    private List<String> etags;

    /** 最终文件MD5 (服务端合并后校验完整性) */
    private String finalMd5;
}