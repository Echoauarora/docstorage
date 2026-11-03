package com.docstorage.service;

import com.docstorage.dto.req.RollbackVersionReq;
import com.docstorage.dto.req.SetFilePermissionReq;
import com.docstorage.dto.res.FileInfoRes;
import com.docstorage.dto.res.VersionListRes;

import java.util.List;

/**
 * 文件管理服务接口
 */
public interface FileManageService {

    /**
     * 获取文件详情 (含权限校�?
     */
    FileInfoRes getFileInfo(Long fileId, Long userId);

    /**
     * 检查用户是否有指定权限
     */
    boolean hasPermission(Long fileId, Long userId, Integer permType);

    /**
     * 设置文件权限
     */
    void setPermission(SetFilePermissionReq req, Long userId);

    /**
     * 从回收站恢复文件
     */
    void recoverFromRecycle(Long fileId, Long userId);

    /**
     * 永久删除文件
     */
    void permanentlyDelete(Long fileId, Long userId);

    /**
     * 软删�?(进入回收�?
     */
    void softDelete(Long fileId, Long userId);

    /**
     * 查看版本列表
     */
    VersionListRes getVersionList(Long fileId, Long userId);

    /**
     * 回滚到指定版�?
     */
    FileInfoRes rollbackVersion(RollbackVersionReq req, Long userId);
}