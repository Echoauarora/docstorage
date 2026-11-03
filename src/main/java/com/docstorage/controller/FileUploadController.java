package com.docstorage.controller;

import com.docstorage.common.Result;
import com.docstorage.dto.req.CompleteUploadReq;
import com.docstorage.dto.req.InitUploadReq;
import com.docstorage.dto.req.UploadPartReq;
import com.docstorage.dto.res.CompleteUploadRes;
import com.docstorage.dto.res.InitUploadRes;
import com.docstorage.service.FileUploadService;
import com.docstorage.util.WebContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 文件分片上传接口
 * <p>
 * 完整调用流程:
 * 1. POST /init → 获取 uploadId (或秒传命中)
 * 2. POST /part → 循环上传分片 (每个分片一个请求)
 * 3. GET /parts → 查询已上传分片 (断点续传)
 * 4. POST /complete → 合并所有分片
 *
 * 所有写请求必须带 X-Idempotent-Key 头 (UUID)
 */
@Tag(name = "文件分片上传")
@RestController
@RequestMapping("/file/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService uploadService;

    /**
     * 1. 初始化分片上传
     * - 根据MD5判断是否秒传
     * - 创建分片上传会话
     */
    @Operation(summary = "初始化分片上传 (含秒传校验)")
    @PostMapping("/init")
    public Result<InitUploadRes> initUpload(@Valid @RequestBody InitUploadReq req) {
        Long userId = WebContextUtil.getUserId();
        return Result.success(uploadService.initUpload(req, userId));
    }

    /**
     * 2. 上传单个分片
     * - 支持 multipart/form-data
     * - 分片内容存 file 字段, 其他参数 query 传递
     */
    @Operation(summary = "上传单个分片")
    @PostMapping(value = "/part", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadPart(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("fileCode") String fileCode,
            @RequestParam("partNumber") Integer partNumber,
            @RequestParam("partSize") Long partSize,
            @RequestParam("file") MultipartFile file) {

        Long userId = WebContextUtil.getUserId();
        try (InputStream is = file.getInputStream()) {
            String etag = uploadService.uploadPart(uploadId, fileCode, partNumber, is, partSize, userId);
            return Result.success(etag);
        } catch (Exception e) {
            return Result.fail("分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 3. 查询已上传分片列表 (断点续传)
     */
    @Operation(summary = "查询已上传分片 (断点续传)")
    @GetMapping("/parts")
    public Result<List<Integer>> queryUploadedParts(@RequestParam String uploadId) {
        return Result.success(uploadService.queryUploadedParts(uploadId));
    }

    /**
     * 4. 合并所有分片为完整文件
     */
    @Operation(summary = "合并所有分片")
    @PostMapping("/complete")
    public Result<CompleteUploadRes> completeUpload(@Valid @RequestBody CompleteUploadReq req) {
        Long userId = WebContextUtil.getUserId();
        return Result.success(uploadService.completeUpload(req, userId));
    }

    /**
     * 5. 取消/清理分片上传
     */
    @Operation(summary = "取消分片上传 (清理临时分片)")
    @DeleteMapping("/abort")
    public Result<Void> abortUpload(@RequestParam String uploadId) {
        uploadService.abortUpload(uploadId);
        return Result.success();
    }
}