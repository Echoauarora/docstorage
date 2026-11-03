package com.docstorage.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 单分片上传请求
 */
@Data
public class UploadPartReq {

    @NotBlank(message = "uploadId不能为空")
    private String uploadId;

    @NotBlank(message = "fileCode不能为空")
    private String fileCode;

    @Positive(message = "分片序号必须大于0")
    private Integer partNumber;

    /** ETag (服务端返回, 客户端回传用于校验) */
    private String etag;

    /** 分片大小 */
    private Long partSize;
}