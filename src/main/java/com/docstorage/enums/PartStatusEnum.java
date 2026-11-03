package com.docstorage.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 分片上传状态枚举
 */
@Getter
@AllArgsConstructor
public enum PartStatusEnum {

    UPLOADED(1, "已上传"),
    MERGED(2, "已合并"),
    EXPIRED(3, "已过期");

    private final Integer code;
    private final String desc;
}