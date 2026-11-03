package com.docstorage.dto.res;

import lombok.Builder;
import lombok.Data;

/**
 * 合并分片响应
 */
@Data
@Builder
public class CompleteUploadRes {

    private Long fileId;

    private String fileCode;

    /** 最终存储路径 */
    private String storagePath;

    /** 文件大小 */
    private Long fileSize;

    /** 版本号 (首次为1) */
    private Integer version;

    /** MD5校验结果: true = 校验通过 */
    private boolean md5Matched;
}