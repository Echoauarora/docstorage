package com.docstorage.storage;

import com.docstorage.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 本地磁盘文件存储策略
 * <p>
 * 基于本地文件系统实现分片存储, 无需外部服务依赖, 开发/测试/WSL 环境都可直接运行
 * <p>
 * 通过 StorageStrategy 接口抽象, 后续可扩展对接对象存储 (MinIO / OSS 等)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalFileStorageStrategy implements StorageStrategy {

    private final StorageProperties properties;

    @Override
    public String getType() {
        return "local";
    }

    // ==================== 初始化 ====================

    @Override
    public void ensureStorageRoot(String storageRoot) {
        try {
            // 正式文件目录
            Path root = Paths.get(properties.getLocal().getRootPath());
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                log.info("Created local storage root: {}", root.toAbsolutePath());
            }
            // 分片临时目录
            Path temp = Paths.get(properties.getLocal().getTempPath());
            if (!Files.exists(temp)) {
                Files.createDirectories(temp);
                log.info("Created local temp root: {}", temp.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new BizException("本地存储目录初始化失败: " + e.getMessage());
        }
    }

    // ==================== 分片上传 ====================

    @Override
    public String initMultipartUpload(String storageRoot, String objectName, String contentType) {
        try {
            // 用 UUID 作为 uploadId
            String uploadId = UUID.randomUUID().toString().replace("-", "");

            // 创建该 uploadId 的临时目录
            Path uploadDir = getUploadDir(uploadId);
            Files.createDirectories(uploadDir);

            log.info("Local storage multipart init: root={}, object={}, uploadId={}",
                    storageRoot, objectName, uploadId);
            return uploadId;
        } catch (IOException e) {
            throw new BizException("本地分片初始化失败: " + e.getMessage());
        }
    }

    @Override
    public String uploadPart(String storageRoot, String objectName, String uploadId,
                             int partNumber, InputStream stream, long size) {
        try {
            // 写入分片临时文件, 原子性保证: 先写 .tmp 再 rename, 避免半写入被扫描到
            Path partFile = getPartFile(uploadId, partNumber);
            Path tmpFile = partFile.resolveSibling(partFile.getFileName() + ".tmp");

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmpFile))) {
                byte[] buffer = new byte[8192];
                long written = 0;
                int n;
                while ((n = stream.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    written += n;
                }
                if (written != size) {
                    log.warn("Part size mismatch: expected={}, actual={}", size, written);
                }
            }

            // 原子性 rename
            Files.move(tmpFile, partFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            // 用分片文件的 MD5 作为 etag
            String etag = Md5Util.calculateStreamMd5(Files.newInputStream(partFile));
            log.debug("Local upload part: uploadId={}, partNumber={}, etag={}", uploadId, partNumber, etag);
            return etag;

        } catch (IOException e) {
            // 清理 .tmp 残留
            Path tmpFile = getPartFile(uploadId, partNumber).resolveSibling(
                    getPartFile(uploadId, partNumber).getFileName() + ".tmp");
            try { Files.deleteIfExists(tmpFile); } catch (IOException ignored) {}

            // 判断是否磁盘满
            if (e.getMessage() != null && (e.getMessage().contains("space") || e.getMessage().contains("full"))) {
                throw new BizException("磁盘空间不足, 无法上传分片");
            }
            throw new BizException("本地分片上传失败: " + e.getMessage());
        }
    }

    @Override
    public List<PartInfo> listUploadedParts(String storageRoot, String objectName, String uploadId) {
        List<PartInfo> parts = new ArrayList<>();
        Path uploadDir = getUploadDir(uploadId);

        if (!Files.exists(uploadDir)) {
            return parts;
        }

        try (Stream<Path> stream = Files.list(uploadDir)) {
            stream.filter(p -> p.toString().endsWith(properties.getLocal().getPartSuffix()))
                  .forEach(p -> {
                      try {
                          String filename = p.getFileName().toString();
                          // 文件名: part_1.part -> 提取序号
                          int underscoreIdx = filename.indexOf('_');
                          int dotIdx = filename.lastIndexOf('.');
                          if (underscoreIdx > 0 && dotIdx > underscoreIdx) {
                              int partNumber = Integer.parseInt(filename.substring(underscoreIdx + 1, dotIdx));
                              long size = Files.size(p);
                              parts.add(new PartInfo(partNumber, null, size));
                          }
                      } catch (Exception e) {
                          log.warn("Skip invalid part file: {}", p.getFileName());
                      }
                  });
        } catch (IOException e) {
            throw new BizException("查询已上传分片失败: " + e.getMessage());
        }

        parts.sort((a, b) -> Integer.compare(a.number(), b.number()));
        return parts;
    }

    @Override
    public void completeMultipartUpload(String storageRoot, String objectName, String uploadId) {
        try {
            // 关键: 从本地磁盘重新扫描分片, 不信任调用方传入的列表
            List<PartInfo> parts = listUploadedParts(storageRoot, objectName, uploadId);
            if (parts.isEmpty()) {
                throw new BizException("没有可合并的分片");
            }

            // 确保目标目录存在
            Path targetPath = getRootPath().resolve(objectName);
            Files.createDirectories(targetPath.getParent());

            // 先写 .tmp 文件, 合并完再 rename (原子性)
            Path tmpTarget = targetPath.resolveSibling(targetPath.getFileName() + ".merge_tmp");

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmpTarget))) {
                for (PartInfo part : parts) {
                    Path partFile = getPartFile(uploadId, part.number());
                    if (!Files.exists(partFile)) {
                        throw new BizException("分片文件丢失: part=" + part.number());
                    }
                    try (InputStream in = Files.newInputStream(partFile)) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = in.read(buffer)) != -1) {
                            out.write(buffer, 0, n);
                        }
                    }
                }
            }

            // 原子 rename
            Files.move(tmpTarget, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            // 清理分片临时目录
            cleanupUploadDir(uploadId);

            log.info("Local multipart completed: root={}, object={}", storageRoot, objectName);

        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("本地分片合并失败: " + e.getMessage());
        }
    }

    @Override
    public void abortMultipartUpload(String storageRoot, String objectName, String uploadId) {
        try {
            cleanupUploadDir(uploadId);
            log.info("Local multipart aborted: uploadId={}", uploadId);
        } catch (Exception e) {
            log.warn("Abort multipart upload cleanup failed: {}", e.getMessage());
        }
    }

    @Override
    public void removeObject(String storageRoot, String objectName) {
        try {
            Path targetPath = getRootPath().resolve(objectName);
            if (Files.exists(targetPath)) {
                Files.delete(targetPath);
                log.info("Local file removed: {}", targetPath.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new BizException("文件删除失败: " + e.getMessage());
        }
    }

    @Override
    public InputStream getObject(String storageRoot, String objectName) {
        try {
            Path targetPath = getRootPath().resolve(objectName);
            if (!Files.exists(targetPath)) {
                throw new BizException("文件不存在: " + objectName);
            }
            return Files.newInputStream(targetPath);
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("获取文件失败: " + e.getMessage());
        }
    }

    @Override
    public boolean objectExists(String storageRoot, String objectName) {
        Path targetPath = getRootPath().resolve(objectName);
        return Files.exists(targetPath);
    }

    @Override
    public String getDownloadUrl(String storageRoot, String objectName, int expireSeconds) {
        // 本地存储返回绝对路径 (生产环境可以是 /api/file/download?path=xxx 这样的下载接口)
        Path targetPath = getRootPath().resolve(objectName);
        return targetPath.toAbsolutePath().toString();
    }

    // ==================== 内部工具方法 ====================

    private Path getRootPath() {
        return Paths.get(properties.getLocal().getRootPath()).toAbsolutePath().normalize();
    }

    private Path getTempPath() {
        return Paths.get(properties.getLocal().getTempPath()).toAbsolutePath().normalize();
    }

    private Path getUploadDir(String uploadId) {
        return getTempPath().resolve(uploadId);
    }

    private Path getPartFile(String uploadId, int partNumber) {
        return getUploadDir(uploadId).resolve("part_" + partNumber + properties.getLocal().getPartSuffix());
    }

    private void cleanupUploadDir(String uploadId) throws IOException {
        Path uploadDir = getUploadDir(uploadId);
        if (Files.exists(uploadDir)) {
            // 递归删除目录
            try (Stream<Path> stream = Files.walk(uploadDir)) {
                stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                      .forEach(p -> {
                          try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                      });
            }
            log.debug("Cleaned up temp upload dir: {}", uploadDir);
        }
    }

    /**
     * 内部 MD5 计算工具 (避免循环依赖, 从 Md5Util 复用思路)
     */
    private static class Md5Util {
        public static String calculateStreamMd5(InputStream stream) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                byte[] buffer = new byte[8192];
                int n;
                while ((n = stream.read(buffer)) != -1) {
                    md.update(buffer, 0, n);
                }
                byte[] digest = md.digest();
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception e) {
                throw new BizException("MD5 计算失败: " + e.getMessage());
            }
        }
    }
}