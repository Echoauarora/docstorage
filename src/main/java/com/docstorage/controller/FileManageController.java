package com.docstorage.controller;

import com.docstorage.common.Result;
import com.docstorage.dto.req.RollbackVersionReq;
import com.docstorage.dto.req.SetFilePermissionReq;
import com.docstorage.dto.res.FileInfoRes;
import com.docstorage.dto.res.VersionListRes;
import com.docstorage.service.FileManageService;
import com.docstorage.util.WebContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 文件管理接口
 * <p>
 * 包含: 详情查询、权限设置、版本管理、回收站操作、文件下载
 */
@Tag(name = "文件管理")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileManageController {

    private final FileManageService fileService;

    @Operation(summary = "获取文件详情")
    @GetMapping("/{fileId}")
    public Result<FileInfoRes> getFileInfo(@PathVariable Long fileId) {
        return Result.success(fileService.getFileInfo(fileId, WebContextUtil.getUserId()));
    }

    @Operation(summary = "设置文件权限")
    @PostMapping("/permission")
    public Result<Void> setPermission(@Valid @RequestBody SetFilePermissionReq req) {
        fileService.setPermission(req, WebContextUtil.getUserId());
        return Result.success();
    }

    @Operation(summary = "软删除 (进入回收站)")
    @DeleteMapping("/{fileId}")
    public Result<Void> softDelete(@PathVariable Long fileId) {
        fileService.softDelete(fileId, WebContextUtil.getUserId());
        return Result.success();
    }

    @Operation(summary = "从回收站恢复文件")
    @PostMapping("/{fileId}/recover")
    public Result<Void> recover(@PathVariable Long fileId) {
        fileService.recoverFromRecycle(fileId, WebContextUtil.getUserId());
        return Result.success();
    }

    @Operation(summary = "永久删除文件")
    @DeleteMapping("/{fileId}/permanent")
    public Result<Void> permanentlyDelete(@PathVariable Long fileId) {
        fileService.permanentlyDelete(fileId, WebContextUtil.getUserId());
        return Result.success();
    }

    @Operation(summary = "查看版本列表")
    @GetMapping("/{fileId}/versions")
    public Result<VersionListRes> getVersions(@PathVariable Long fileId) {
        return Result.success(fileService.getVersionList(fileId, WebContextUtil.getUserId()));
    }

    @Operation(summary = "版本回滚")
    @PostMapping("/version/rollback")
    public Result<FileInfoRes> rollbackVersion(@Valid @RequestBody RollbackVersionReq req) {
        return Result.success(fileService.rollbackVersion(req, WebContextUtil.getUserId()));
    }

    @Operation(summary = "生成预签名下载URL (15分钟有效)")
    @GetMapping("/{fileId}/download-url")
    public Result<Map<String, String>> getDownloadUrl(@PathVariable Long fileId) {
        FileInfoRes info = fileService.getFileInfo(fileId, WebContextUtil.getUserId());
        // 这里简化处理, 实际需要从DB获取storagePath/bucket
        // 直接返回一个占位, 文件下载由下载代理服务处理
        return Result.success(Map.of("fileId", String.valueOf(fileId), "filename", info.getFilename()));
    }
}