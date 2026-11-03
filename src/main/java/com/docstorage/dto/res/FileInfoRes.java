package com.docstorage.dto.res;

import lombok.Builder;
import lombok.Data;

/**
 * 文件元数据响应
 */
@Data
@Builder
public class FileInfoRes {

    private Long id;
    private String fileCode;
    private String filename;
    private String fileExt;
    private Long fileSize;
    private String fileMd5;
    private String fileType;
    private String storagePath;
    private String thumbnailUrl;
    private Integer version;
    private Integer fileStatus;
    private Long ownerId;
    private String createTime;
}