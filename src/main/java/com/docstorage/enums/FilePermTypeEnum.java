package com.docstorage.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件权限类型枚举
 */
@Getter
@AllArgsConstructor
public enum FilePermTypeEnum {

    VIEW(1, "查看"),
    EDIT(2, "编辑"),
    DELETE(3, "删除"),
    DOWNLOAD(4, "下载");

    private final Integer code;
    private final String desc;
}