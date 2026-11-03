package com.docstorage.dto.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 分片初始化响应
 */
@Data
@Builder
public class InitUploadRes {

    /** uploadId, 后续分片上传/合并都要传 */
    private String uploadId;

    /** 文件编码 (客户端传入, 用于追踪) */
    private String fileCode;

    /** 实际分片大小 (与请求一致或使用默认值) */
    private Long partSize;

    /** 总分片数 */
    private Integer totalParts;

    /**
     * 秒传标识: true = 文件已存在, 直接复用, 无需上传
     * 此时 uploadId 为 null
     */
    private boolean instantUpload;

    /** 秒传命中的已有文件ID */
    private Long existingFileId;

    /** 已上传分片序号列表 (断点续传时返回) */
    private List<Integer> uploadedParts;
}