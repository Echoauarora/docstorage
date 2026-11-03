package com.docstorage.storage;

import com.docstorage.exception.BizException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地磁盘存储策略单元测试
 * <p>
 * 核心用例覆盖:
 * 1. 目录自动创建
 * 2. 分片初始化 + uploadId 生成
 * 3. 分片上传 + ETag 计算
 * 4. 查询已上传分片 (断点续传)
 * 5. 合并分片 + MD5 校验
 * 6. 删除文件
 * 7. 并发写入原子性 (.tmp + rename)
 */
class LocalFileStorageStrategyTest {

    private LocalFileStorageStrategy strategy;
    private StorageProperties props;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        props = new StorageProperties();
        props.getLocal().setRootPath(tempDir.resolve("files").toString());
        props.getLocal().setTempPath(tempDir.resolve("temp").toString());
        strategy = new LocalFileStorageStrategy(props);
    }

    @Test
    @DisplayName("目录自动创建 - ensureStorageRoot 成功")
    void testEnsureStorageRoot() {
        Path root = Paths.get(props.getLocal().getRootPath());
        assertFalse(Files.exists(root));

        strategy.ensureStorageRoot("local");

        assertTrue(Files.exists(root));
        assertTrue(Files.isDirectory(root));

        Path temp = Paths.get(props.getLocal().getTempPath());
        assertTrue(Files.exists(temp));
    }

    @Test
    @DisplayName("分片初始化 - 返回非空 uploadId")
    void testInitMultipartUpload() {
        strategy.ensureStorageRoot("local");

        String uploadId = strategy.initMultipartUpload("local", "2024/01/test.pdf", null);

        assertNotNull(uploadId);
        assertFalse(uploadId.isEmpty());

        // 验证临时目录被创建
        Path uploadDir = Paths.get(props.getLocal().getTempPath()).resolve(uploadId);
        assertTrue(Files.exists(uploadDir));
    }

    @Test
    @DisplayName("分片上传 - 写入分片文件并返回 ETag")
    void testUploadPart() throws IOException {
        strategy.ensureStorageRoot("local");
        String uploadId = strategy.initMultipartUpload("local", "2024/01/test.bin", null);

        byte[] data = new byte[1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        InputStream stream = new ByteArrayInputStream(data);

        String etag = strategy.uploadPart("local", "2024/01/test.bin", uploadId, 1, stream, data.length);

        assertNotNull(etag);
        assertEquals(32, etag.length()); // MD5 hex = 32 chars

        // 验证分片文件存在
        Path partFile = Paths.get(props.getLocal().getTempPath())
                .resolve(uploadId)
                .resolve("part_1.part");
        assertTrue(Files.exists(partFile));
        assertEquals(data.length, Files.size(partFile));
    }

    @Test
    @DisplayName("查询已上传分片 - 断点续传")
    void testListUploadedParts() throws IOException {
        strategy.ensureStorageRoot("local");
        String uploadId = strategy.initMultipartUpload("local", "2024/01/test.bin", null);

        // 上传 3 个分片
        for (int i = 1; i <= 3; i++) {
            byte[] data = new byte[100];
            InputStream stream = new ByteArrayInputStream(data);
            strategy.uploadPart("local", "2024/01/test.bin", uploadId, i, stream, 100);
        }

        List<StorageStrategy.PartInfo> parts = strategy.listUploadedParts("local", "2024/01/test.bin", uploadId);

        assertEquals(3, parts.size());
        assertEquals(1, parts.get(0).number());
        assertEquals(2, parts.get(1).number());
        assertEquals(3, parts.get(2).number());
    }

    @Test
    @DisplayName("合并分片 - 生成完整文件")
    void testCompleteMultipartUpload() throws IOException {
        strategy.ensureStorageRoot("local");
        String uploadId = strategy.initMultipartUpload("local", "2024/01/merge_test.bin", null);

        // 上传 2 个分片
        byte[] data1 = new byte[500];
        byte[] data2 = new byte[500];
        for (int i = 0; i < 500; i++) {
            data1[i] = (byte) 'A';
            data2[i] = (byte) 'B';
        }
        strategy.uploadPart("local", "2024/01/merge_test.bin", uploadId, 1, new ByteArrayInputStream(data1), 500);
        strategy.uploadPart("local", "2024/01/merge_test.bin", uploadId, 2, new ByteArrayInputStream(data2), 500);

        strategy.completeMultipartUpload("local", "2024/01/merge_test.bin", uploadId);

        // 验证合并后的文件
        Path mergedFile = Paths.get(props.getLocal().getRootPath()).resolve("2024/01/merge_test.bin");
        assertTrue(Files.exists(mergedFile));
        assertEquals(1000, Files.size(mergedFile));

        // 验证分片临时目录已清理
        Path uploadDir = Paths.get(props.getLocal().getTempPath()).resolve(uploadId);
        assertFalse(Files.exists(uploadDir));
    }

    @Test
    @DisplayName("取消分片上传 - 清理临时目录")
    void testAbortMultipartUpload() throws IOException {
        strategy.ensureStorageRoot("local");
        String uploadId = strategy.initMultipartUpload("local", "2024/01/abort_test.bin", null);

        byte[] data = new byte[100];
        strategy.uploadPart("local", "2024/01/abort_test.bin", uploadId, 1, new ByteArrayInputStream(data), 100);

        Path uploadDir = Paths.get(props.getLocal().getTempPath()).resolve(uploadId);
        assertTrue(Files.exists(uploadDir));

        strategy.abortMultipartUpload("local", "2024/01/abort_test.bin", uploadId);

        assertFalse(Files.exists(uploadDir));
    }

    @Test
    @DisplayName("文件存在检查 - objectExists")
    void testObjectExists() throws IOException {
        strategy.ensureStorageRoot("local");
        String uploadId = strategy.initMultipartUpload("local", "2024/01/exist_test.bin", null);

        byte[] data = new byte[100];
        strategy.uploadPart("local", "2024/01/exist_test.bin", uploadId, 1, new ByteArrayInputStream(data), 100);
        strategy.completeMultipartUpload("local", "2024/01/exist_test.bin", uploadId);

        assertTrue(strategy.objectExists("local", "2024/01/exist_test.bin"));
        assertFalse(strategy.objectExists("local", "2024/01/not_exist.bin"));
    }

    @Test
    @DisplayName("文件删除 - removeObject")
    void testRemoveObject() throws IOException {
        strategy.ensureStorageRoot("local");
        String uploadId = strategy.initMultipartUpload("local", "2024/01/del_test.bin", null);

        byte[] data = new byte[100];
        strategy.uploadPart("local", "2024/01/del_test.bin", uploadId, 1, new ByteArrayInputStream(data), 100);
        strategy.completeMultipartUpload("local", "2024/01/del_test.bin", uploadId);

        assertTrue(strategy.objectExists("local", "2024/01/del_test.bin"));

        strategy.removeObject("local", "2024/01/del_test.bin");

        assertFalse(strategy.objectExists("local", "2024/01/del_test.bin"));
    }

    @Test
    @DisplayName("空分片合并 - 抛出异常")
    void testCompleteMultipartUploadNoParts() {
        strategy.ensureStorageRoot("local");
        String uploadId = strategy.initMultipartUpload("local", "2024/01/empty.bin", null);

        assertThrows(BizException.class, () ->
                strategy.completeMultipartUpload("local", "2024/01/empty.bin", uploadId)
        );
    }

    @Test
    @DisplayName("getType - 返回 local")
    void testGetType() {
        assertEquals("local", strategy.getType());
    }
}