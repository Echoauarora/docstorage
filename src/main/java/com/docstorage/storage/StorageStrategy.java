package com.docstorage.storage;

/**
 * 存储策略接口
 * <p>
 * 统一抽象文件存储的核心操作, 实现类可自由切换
 * ┌─────────────────────────────────────────────────────────┐
 * │                StorageStrategy (接口)                    │
 * ├─────────────────────────────────────────────────────────┤
 * │ ┌──────────────────────────────────────────────────┐    │
 * │ │ LocalFileStorageStrategy (默认, 本地磁盘分片存储) │    │
 * │ └──────────────────────────────────────────────────┘    │
 * │                    ↑ 预留扩展点                          │
 * │ ┌──────────────────────────────────────────────────┐    │
 * │ │ 未来可扩展: OSSStorageStrategy / S3Strategy ...   │    │
 * │ └──────────────────────────────────────────────────┘    │
 * └─────────────────────────────────────────────────────────┘
 * <p>
 * 设计原则:
 * 1. 接口只暴露业务层关心的操作, 不泄漏底层 SDK 细节
 * 2. Bucket / rootPath 概念统一抽象为 "存储根", 上层通过 storagePath 定位
 * 3. 策略切换对 Service 层透明, 只通过 StorageStrategyFactory 获取实例
 */
public interface StorageStrategy {

    /** 策略类型标识 */
    String getType();

    /**
     * 初始化存储根 (Bucket / 目录)
     * 确保存储根存在, 不存在则创建
     */
    void ensureStorageRoot(String storageRoot);

    /**
     * 初始化分片上传
     * @param storageRoot 存储根 (bucket名 或 根路径)
     * @param objectName  对象相对路径 (storagePath)
     * @param contentType 文件类型
     * @return uploadId 分片上传句柄
     */
    String initMultipartUpload(String storageRoot, String objectName, String contentType);

    /**
     * 上传单个分片
     * @param storageRoot 存储根
     * @param objectName  对象相对路径
     * @param uploadId    分片上传句柄
     * @param partNumber  分片序号 (从1开始)
     * @param stream      分片输入流
     * @param size        分片大小
     * @return 分片校验标识 (本地文件MD5, 对象存储则为ETag)
     */
    String uploadPart(String storageRoot, String objectName, String uploadId,
                      int partNumber, java.io.InputStream stream, long size);

    /**
     * 查询已上传分片列表 (断点续传核心)
     * @return 分片信息列表
     */
    java.util.List<PartInfo> listUploadedParts(String storageRoot, String objectName, String uploadId);

    /**
     * 合并所有分片为完整文件
     * <p>
     * 关键: 实现类内部必须重新 listParts 确认分片完整性, 不信任客户端传入
     */
    void completeMultipartUpload(String storageRoot, String objectName, String uploadId);

    /**
     * 取消分片上传 (清理残留)
     */
    void abortMultipartUpload(String storageRoot, String objectName, String uploadId);

    /**
     * 删除文件 (物理删除)
     */
    void removeObject(String storageRoot, String objectName);

    /**
     * 获取文件输入流 (下载/MD5校验/缩略图生成)
     */
    java.io.InputStream getObject(String storageRoot, String objectName);

    /**
     * 检查文件是否存在
     */
    boolean objectExists(String storageRoot, String objectName);

    /**
     * 获取预签名下载URL (本地存储可返回本地路径或流式下载接口URL)
     * @param expireSeconds 有效期 (秒)
     */
    String getDownloadUrl(String storageRoot, String objectName, int expireSeconds);

    /**
     * 分片信息 POJO
     */
    record PartInfo(int number, String etag, long size) {}
}