package com.docstorage.service.impl;

import com.docstorage.dto.req.InitUploadReq;
import com.docstorage.dto.res.InitUploadRes;
import com.docstorage.entity.DocFile;
import com.docstorage.mapper.DocFileMapper;
import com.docstorage.mapper.DocUploadPartMapper;
import com.docstorage.mapper.DocFileVersionMapper;
import com.docstorage.service.AsyncLogService;
import com.docstorage.storage.StorageStrategy;
import com.docstorage.storage.StorageStrategyFactory;
import com.docstorage.storage.StorageProperties;
import com.docstorage.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 文件上传服务单元测试
 * <p>
 * 核心用例覆盖:
 * 1. MD5秒传命中场景
 * 2. 分片初始化成功
 * 3. 幂等性: 相同fileCode重复初始化
 */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceImplTest {

    @Mock
    private StorageStrategyFactory factory;
    @Mock
    private StorageStrategy storageStrategy;
    @Mock
    private StorageProperties storageProperties;
    @Mock
    private DocFileMapper fileMapper;
    @Mock
    private DocUploadPartMapper uploadPartMapper;
    @Mock
    private DocFileVersionMapper versionMapper;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private AsyncLogService asyncLogService;

    @InjectMocks
    private FileUploadServiceImpl service;

    private final String TEST_FILE_CODE = "FILE_20240101_001";
    private final String TEST_MD5 = "d41d8cd98f00b204e9800998ecf8427e";
    private final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        when(factory.getStrategy()).thenReturn(storageStrategy);
        when(storageStrategy.getType()).thenReturn("local");
        when(storageProperties.getDefaultPartSize()).thenReturn(5242880L);
    }

    /**
     * 测试1: MD5秒传命中
     * 相同MD5的文件已存在, 应直接复用, 不触发存储层操作
     */
    @Test
    @DisplayName("MD5秒传命中 - 复用已有文件")
    void testInstantUploadHit() {
        DocFile existing = new DocFile();
        existing.setId(100L);
        existing.setFileMd5(TEST_MD5);
        existing.setStoragePath("2024/01/01/existing_file.pdf");
        existing.setFileSize(1024L);

        when(redisUtil.get(anyString())).thenReturn(null);
        when(fileMapper.selectExistingByMd5(TEST_MD5)).thenReturn(existing);
        when(fileMapper.selectOne(any())).thenReturn(null);

        InitUploadReq req = new InitUploadReq();
        req.setFileCode(TEST_FILE_CODE);
        req.setFilename("test.pdf");
        req.setFileMd5(TEST_MD5);
        req.setFileSize(1024L);

        InitUploadRes res = service.initUpload(req, TEST_USER_ID);

        assertTrue(res.isInstantUpload());
        assertNotNull(res.getExistingFileId());
        assertNull(res.getUploadId());

        verify(storageStrategy, never()).initMultipartUpload(anyString(), anyString(), anyString());
    }

    /**
     * 测试2: 正常分片初始化 (非秒传)
     */
    @Test
    @DisplayName("分片初始化 - 正常创建分片")
    void testNormalInitUpload() {
        when(redisUtil.get(anyString())).thenReturn(null);
        when(fileMapper.selectExistingByMd5(TEST_MD5)).thenReturn(null);
        when(fileMapper.selectOne(any())).thenReturn(null);
        when(storageStrategy.initMultipartUpload(anyString(), anyString(), anyString())).thenReturn("upload-12345");

        InitUploadReq req = new InitUploadReq();
        req.setFileCode(TEST_FILE_CODE);
        req.setFilename("large_file.zip");
        req.setFileMd5(TEST_MD5);
        req.setFileSize(52428800L);

        InitUploadRes res = service.initUpload(req, TEST_USER_ID);

        assertFalse(res.isInstantUpload());
        assertEquals("upload-12345", res.getUploadId());
        assertEquals(10, res.getTotalParts());
        assertEquals(5242880L, res.getPartSize());

        verify(fileMapper).insert(any(DocFile.class));
        verify(redisUtil).set(anyString(), anyString(), anyLong());
    }

    /**
     * 测试3: 幂等性 - 相同fileCode不能重复初始化
     */
    @Test
    @DisplayName("幂等校验 - 相同fileCode重复初始化抛异常")
    void testIdempotentInitUpload() {
        DocFile exist = new DocFile();
        exist.setId(1L);
        exist.setFileCode(TEST_FILE_CODE);
        exist.setFileStatus(2);

        when(fileMapper.selectOne(any())).thenReturn(exist);

        InitUploadReq req = new InitUploadReq();
        req.setFileCode(TEST_FILE_CODE);
        req.setFilename("dup.txt");
        req.setFileMd5(TEST_MD5);
        req.setFileSize(1024L);

        Exception ex = assertThrows(Exception.class, () -> service.initUpload(req, TEST_USER_ID));
        assertTrue(ex.getMessage().contains("文件编码已存在"));
    }
}