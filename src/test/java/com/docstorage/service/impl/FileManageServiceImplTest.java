package com.docstorage.service.impl;

import com.docstorage.entity.DocFile;
import com.docstorage.entity.DocFilePermission;
import com.docstorage.exception.AuthException;
import com.docstorage.mapper.DocFileMapper;
import com.docstorage.mapper.DocFilePermissionMapper;
import com.docstorage.mapper.DocFileVersionMapper;
import com.docstorage.mapper.DocRecycleBinMapper;
import com.docstorage.service.AsyncLogService;
import com.docstorage.storage.StorageStrategy;
import com.docstorage.storage.StorageStrategyFactory;
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
 * 文件权限服务单元测试
 * <p>
 * 核心用例:
 * 1. 文件所有者权限(天然全权限)
 * 2. 缓存命中场景
 * 3. 权限拒绝场景
 * 4. 缓存主动失效
 */
@ExtendWith(MockitoExtension.class)
class FileManageServiceImplTest {

    @Mock
    private StorageStrategyFactory factory;
    @Mock
    private StorageStrategy storageStrategy;
    @Mock
    private DocFileMapper fileMapper;
    @Mock
    private DocFilePermissionMapper permissionMapper;
    @Mock
    private DocFileVersionMapper versionMapper;
    @Mock
    private DocRecycleBinMapper recycleBinMapper;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private AsyncLogService asyncLogService;

    @InjectMocks
    private FileManageServiceImpl service;

    private static final Long FILE_ID = 100L;
    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 999L;

    @BeforeEach
    void setUp() {
        when(factory.getStrategy()).thenReturn(storageStrategy);
    }

    private DocFile buildMockFile() {
        DocFile file = new DocFile();
        file.setId(FILE_ID);
        file.setFilename("test.txt");
        file.setOwnerId(OWNER_ID);
        file.setDeleted(0);
        return file;
    }

    /**
     * 测试1: 文件所有者天然拥有全部权限
     */
    @Test
    @DisplayName("权限校验 - 文件所有者全权限")
    void testOwnerHasAllPermission() {
        when(fileMapper.selectById(FILE_ID)).thenReturn(buildMockFile());
        boolean result = service.hasPermission(FILE_ID, OWNER_ID, 3);
        assertTrue(result);
    }

    /**
     * 测试2: Redis 缓存命中, 直接返回, 不查DB
     */
    @Test
    @DisplayName("权限校验 - Redis缓存命中直接返回")
    void testPermissionCacheHit() {
        when(redisUtil.get(anyString())).thenReturn(true);

        boolean result = service.hasPermission(FILE_ID, OTHER_USER_ID, 1);

        assertTrue(result);
        verify(permissionMapper, never()).countUserHasPermission(anyLong(), anyLong(), anyInt());
    }

    /**
     * 测试3: 缓存未命中, 查DB并回写缓存
     */
    @Test
    @DisplayName("权限校验 - 缓存未命中查DB并回写")
    void testPermissionCacheMiss() {
        when(redisUtil.get(anyString())).thenReturn(null);
        when(permissionMapper.countUserHasPermission(FILE_ID, OTHER_USER_ID, 1)).thenReturn(1);

        boolean result = service.hasPermission(FILE_ID, OTHER_USER_ID, 1);

        assertTrue(result);
        verify(redisUtil).set(anyString(), eq(true), anyLong());
    }

    /**
     * 测试4: 缓存主动失效 - 权限变更后清除
     */
    @Test
    @DisplayName("权限设置 - 触发缓存主动失效")
    void testPermissionCacheInvalidate() {
        var req = new com.docstorage.dto.req.SetFilePermissionReq();
        req.setFileId(FILE_ID);
        req.setUserId(OTHER_USER_ID);
        req.setPermType(1);

        service.setPermission(req, 1L);

        verify(permissionMapper).insert(any(DocFilePermission.class));
        verify(redisUtil, atLeastOnce()).deleteByPattern(anyString());
    }
}