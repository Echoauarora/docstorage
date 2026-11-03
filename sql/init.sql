-- =====================================================
-- 企业文档权限与分片存储系统 - 数据库初始化脚本
-- MySQL 8.0+
-- =====================================================

CREATE DATABASE IF NOT EXISTS docstorage 
  DEFAULT CHARACTER SET utf8mb4 
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE docstorage;

-- =====================================================
-- 1. 用户表 (sys_user)
-- =====================================================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    username        VARCHAR(64)     NOT NULL                       COMMENT '用户名(唯一)',
    password        VARCHAR(128)    NOT NULL                       COMMENT '密码(BCrypt加密)',
    nickname        VARCHAR(64)     DEFAULT NULL                   COMMENT '昵称',
    email           VARCHAR(128)    DEFAULT NULL                   COMMENT '邮箱',
    phone           VARCHAR(32)     DEFAULT NULL                   COMMENT '手机号',
    avatar_url      VARCHAR(512)    DEFAULT NULL                   COMMENT '头像URL',
    status          TINYINT         NOT NULL DEFAULT 1             COMMENT '状态:1-正常,0-禁用',
    last_login_time DATETIME        DEFAULT NULL                   COMMENT '最后登录时间',
    last_login_ip   VARCHAR(64)     DEFAULT NULL                   COMMENT '最后登录IP',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP 
                                         ON UPDATE CURRENT_TIMESTAMP  COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0             COMMENT '逻辑删除:0-未删除,1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_email (email),
    KEY idx_phone (phone),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- =====================================================
-- 2. 角色表 (sys_role)
-- =====================================================
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    role_code       VARCHAR(64)     NOT NULL                       COMMENT '角色编码(唯一)',
    role_name       VARCHAR(64)     NOT NULL                       COMMENT '角色名称',
    description     VARCHAR(256)    DEFAULT NULL                   COMMENT '角色描述',
    sort_order      INT             NOT NULL DEFAULT 0             COMMENT '排序',
    status          TINYINT         NOT NULL DEFAULT 1             COMMENT '状态:1-正常,0-禁用',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP 
                                         ON UPDATE CURRENT_TIMESTAMP  COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0             COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- =====================================================
-- 3. 权限/资源表 (sys_permission)
-- =====================================================
DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    parent_id       BIGINT          NOT NULL DEFAULT 0             COMMENT '父级ID,0表示顶级',
    perm_code       VARCHAR(128)    NOT NULL                       COMMENT '权限编码(唯一),如:file:view',
    perm_name       VARCHAR(64)     NOT NULL                       COMMENT '权限名称',
    resource_type   TINYINT         NOT NULL DEFAULT 1             COMMENT '资源类型:1-文档,2-文件夹,3-操作',
    sort_order      INT             NOT NULL DEFAULT 0             COMMENT '排序',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP 
                                         ON UPDATE CURRENT_TIMESTAMP  COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0             COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限资源表';

-- =====================================================
-- 4. 用户-角色关联表 (sys_user_role)
-- =====================================================
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    user_id         BIGINT          NOT NULL                       COMMENT '用户ID',
    role_id         BIGINT          NOT NULL                       COMMENT '角色ID',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- =====================================================
-- 5. 角色-权限关联表 (sys_role_permission)
-- =====================================================
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    role_id         BIGINT          NOT NULL                       COMMENT '角色ID',
    permission_id   BIGINT          NOT NULL                       COMMENT '权限ID',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- =====================================================
-- 6. 文件元数据表 (doc_file)
-- =====================================================
DROP TABLE IF EXISTS doc_file;
CREATE TABLE doc_file (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    file_code       VARCHAR(64)     NOT NULL                       COMMENT '文件业务编码(幂等用,唯一)',
    filename        VARCHAR(256)    NOT NULL                       COMMENT '原始文件名',
    file_ext        VARCHAR(32)     DEFAULT NULL                   COMMENT '文件扩展名',
    file_size       BIGINT          NOT NULL DEFAULT 0             COMMENT '文件大小(字节)',
    file_md5        VARCHAR(64)     NOT NULL                       COMMENT '文件MD5(秒传/去重用)',
    file_type       VARCHAR(64)     DEFAULT NULL                   COMMENT '文件MIME类型',
    storage_path    VARCHAR(512)    NOT NULL                       COMMENT 'MinIO存储路径',
    bucket_name     VARCHAR(128)    NOT NULL                       COMMENT 'MinIO桶名',
    thumbnail_url   VARCHAR(512)    DEFAULT NULL                   COMMENT '缩略图URL(异步生成)',
    upload_id       VARCHAR(128)    DEFAULT NULL                   COMMENT '分片上传ID(MinIO)',
    owner_id        BIGINT          NOT NULL                       COMMENT '所属用户ID',
    parent_id       BIGINT          DEFAULT 0                      COMMENT '父级文件夹ID,0表示根目录',
    file_status     TINYINT         NOT NULL DEFAULT 1             COMMENT '文件状态:1-正常,2-上传中,3-已删除',
    version         INT             NOT NULL DEFAULT 1             COMMENT '当前版本号',
    share_code      VARCHAR(32)     DEFAULT NULL                   COMMENT '分享码',
    expire_time     DATETIME        DEFAULT NULL                   COMMENT '过期时间(分享)',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP 
                                         ON UPDATE CURRENT_TIMESTAMP  COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0             COMMENT '逻辑删除:0-未删除,1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_code (file_code),
    KEY idx_file_md5 (file_md5),
    KEY idx_owner_id (owner_id),
    KEY idx_parent_id (parent_id),
    KEY idx_owner_parent (owner_id, parent_id, deleted),
    KEY idx_upload_id (upload_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件元数据表';

-- =====================================================
-- 7. 文件权限表 (doc_file_permission)
-- =====================================================
DROP TABLE IF EXISTS doc_file_permission;
CREATE TABLE doc_file_permission (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    file_id         BIGINT          NOT NULL                       COMMENT '文件ID',
    user_id         BIGINT          DEFAULT NULL                   COMMENT '用户ID(与role_id至少一个)',
    role_id         BIGINT          DEFAULT NULL                   COMMENT '角色ID',
    perm_type       TINYINT         NOT NULL                       COMMENT '权限类型:1-查看,2-编辑,3-删除,4-下载',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP 
                                         ON UPDATE CURRENT_TIMESTAMP  COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_file_id (file_id),
    KEY idx_user_id (user_id),
    KEY idx_role_id (role_id),
    KEY idx_file_user_perm (file_id, user_id, perm_type),
    KEY idx_file_role_perm (file_id, role_id, perm_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件权限表';

-- =====================================================
-- 8. 分片记录表 (doc_upload_part)
-- =====================================================
DROP TABLE IF EXISTS doc_upload_part;
CREATE TABLE doc_upload_part (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    upload_id       VARCHAR(128)    NOT NULL                       COMMENT 'MinIO上传ID',
    file_code       VARCHAR(64)     NOT NULL                       COMMENT '文件业务编码',
    part_number     INT             NOT NULL                       COMMENT '分片序号(从1开始)',
    part_size       BIGINT          NOT NULL DEFAULT 0             COMMENT '分片大小(字节)',
    part_md5        VARCHAR(64)     DEFAULT NULL                   COMMENT '分片MD5(用于完整性校验)',
    etag            VARCHAR(128)    DEFAULT NULL                   COMMENT 'MinIO ETag',
    status          TINYINT         NOT NULL DEFAULT 1             COMMENT '状态:1-已上传,2-已合并',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP 
                                         ON UPDATE CURRENT_TIMESTAMP  COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_upload_part (upload_id, part_number),
    KEY idx_file_code (file_code),
    KEY idx_upload_id (upload_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片上传记录表';

-- =====================================================
-- 9. 文件版本表 (doc_file_version)
-- =====================================================
DROP TABLE IF EXISTS doc_file_version;
CREATE TABLE doc_file_version (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    file_id         BIGINT          NOT NULL                       COMMENT '文件ID',
    version_number  INT             NOT NULL                       COMMENT '版本号',
    file_size       BIGINT          NOT NULL DEFAULT 0             COMMENT '该版本文件大小',
    file_md5        VARCHAR(64)     NOT NULL                       COMMENT '该版本文件MD5',
    storage_path    VARCHAR(512)    NOT NULL                       COMMENT 'MinIO存储路径',
    bucket_name     VARCHAR(128)    NOT NULL                       COMMENT 'MinIO桶名',
    change_log      VARCHAR(512)    DEFAULT NULL                   COMMENT '变更说明',
    operator_id     BIGINT          NOT NULL                       COMMENT '操作人ID',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_file_id (file_id),
    KEY idx_file_version (file_id, version_number),
    KEY idx_file_md5 (file_md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件版本表';

-- =====================================================
-- 10. 回收站表 (doc_recycle_bin)
-- =====================================================
DROP TABLE IF EXISTS doc_recycle_bin;
CREATE TABLE doc_recycle_bin (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    file_id         BIGINT          NOT NULL                       COMMENT '文件ID',
    file_code       VARCHAR(64)     NOT NULL                       COMMENT '文件业务编码',
    filename        VARCHAR(256)    NOT NULL                       COMMENT '文件名',
    file_size       BIGINT          NOT NULL DEFAULT 0             COMMENT '文件大小',
    owner_id        BIGINT          NOT NULL                       COMMENT '所有者ID',
    delete_user_id  BIGINT          NOT NULL                       COMMENT '删除操作人ID',
    expire_time     DATETIME        NOT NULL                       COMMENT '过期时间(默认30天)',
    delete_reason   VARCHAR(256)    DEFAULT NULL                   COMMENT '删除原因',
    status          TINYINT         NOT NULL DEFAULT 1             COMMENT '状态:1-回收站中,2-已恢复,3-已永久删除',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP 
                                         ON UPDATE CURRENT_TIMESTAMP  COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_file_id (file_id),
    KEY idx_owner_id (owner_id),
    KEY idx_expire_time (expire_time),
    KEY idx_owner_status (owner_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回收站表';

-- =====================================================
-- 11. 访问日志表 (doc_access_log)
-- =====================================================
DROP TABLE IF EXISTS doc_access_log;
CREATE TABLE doc_access_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    trace_id        VARCHAR(64)     DEFAULT NULL                   COMMENT 'SkyWalking链路追踪ID',
    user_id         BIGINT          DEFAULT NULL                   COMMENT '用户ID',
    file_id         BIGINT          DEFAULT NULL                   COMMENT '文件ID',
    access_type     VARCHAR(32)     NOT NULL                       COMMENT '操作类型:view,edit,delete,upload,download',
    access_ip       VARCHAR(64)     DEFAULT NULL                   COMMENT '访问IP',
    user_agent      VARCHAR(512)    DEFAULT NULL                   COMMENT 'UA',
    duration        INT             DEFAULT NULL                   COMMENT '耗时(ms)',
    result          TINYINT         NOT NULL DEFAULT 1             COMMENT '结果:1-成功,0-失败',
    error_msg       VARCHAR(512)    DEFAULT NULL                   COMMENT '错误信息',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '访问时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_file_id (file_id),
    KEY idx_create_time (create_time),
    KEY idx_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访问日志表';

-- =====================================================
-- 12. 操作幂等表 (doc_idempotent)
-- =====================================================
DROP TABLE IF EXISTS doc_idempotent;
CREATE TABLE doc_idempotent (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    idempotent_key  VARCHAR(128)    NOT NULL                       COMMENT '幂等键(业务编号)',
    module          VARCHAR(64)     NOT NULL                       COMMENT '业务模块',
    result_data     TEXT            DEFAULT NULL                   COMMENT '首次执行结果(JSON),用于重复请求直接返回',
    status          TINYINT         NOT NULL DEFAULT 1             COMMENT '状态:1-处理中,2-已完成',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    expire_time     DATETIME        NOT NULL                       COMMENT '过期时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotent_key (idempotent_key),
    KEY idx_module (module),
    KEY idx_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等控制表';

-- =====================================================
-- 索引优化说明 (面试讲解要点)
-- =====================================================
/*
1. sys_user.uk_username: 用户登录高频查询,唯一索引保证数据一致性
2. doc_file.idx_file_md5: MD5秒传核心索引,等值查询O(1)
3. doc_file.idx_owner_parent: 联合索引覆盖列表查询场景,避免回表
4. doc_upload_part.uk_upload_part: 唯一索引防止重复分片写入,同时加速查询
5. doc_file_permission.idx_file_user_perm: 三列联合索引,覆盖权限校验等值查询
6. doc_access_log.idx_create_time: 日志表时间维度查询,便于按时间范围归档
7. 所有表都建立了 deleted 字段 + 业务索引的联合索引考虑(查询时WHERE deleted=0)
*/

-- =====================================================
-- 初始化数据
-- =====================================================
-- 默认管理员密码: admin123 (BCrypt加密后)
INSERT INTO sys_user (username, password, nickname, email, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '超级管理员', 'admin@docstorage.com', 1),
('test', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '测试用户', 'test@docstorage.com', 1);

INSERT INTO sys_role (role_code, role_name, description, sort_order, status) VALUES
('ROLE_ADMIN', '超级管理员', '拥有全部权限', 1, 1),
('ROLE_USER', '普通用户', '基础文档操作权限', 2, 1),
('ROLE_VIEWER', '只读用户', '仅查看权限', 3, 1);

INSERT INTO sys_permission (perm_code, perm_name, resource_type, sort_order) VALUES
('file:view', '查看文档', 1, 1),
('file:edit', '编辑文档', 1, 2),
('file:delete', '删除文档', 1, 3),
('file:download', '下载文档', 1, 4),
('file:upload', '上传文档', 1, 5),
('file:share', '分享文档', 1, 6),
('file:manage', '管理全部文档', 1, 7);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1), (2, 2);

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7),
(2, 1), (2, 2), (2, 4), (2, 5),
(3, 1), (3, 4);