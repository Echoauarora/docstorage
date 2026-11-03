package com.docstorage.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件状态枚举
 */
@Getter
@AllArgsConstructor
public enum FileStatusEnum {

    UPLOADING(1, "上传中"),
    NORMAL(2, "正常"),
    DELETED(3, "已删除"),
    MERGING(4, "合并中");

    private final Integer code;
    private final String desc;

    public static FileStatusEnum of(Integer code) {
        if (code == null) return null;
        for (FileStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}