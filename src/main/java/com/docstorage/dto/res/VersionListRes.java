package com.docstorage.dto.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 版本列表响应
 */
@Data
@Builder
public class VersionListRes {

    private Long fileId;
    private String filename;
    private Integer currentVersion;
    private List<VersionItem> versions;

    @Data
    @Builder
    public static class VersionItem {
        private Integer versionNumber;
        private Long fileSize;
        private String fileMd5;
        private String changeLog;
        private String operatorName;
        private String createTime;
    }
}