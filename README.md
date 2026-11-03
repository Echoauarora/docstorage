# 企业文档权限与分片存储系统

> 基于 SpringBoot 3.2 + MySQL8 + Redis7 + Kafka 的企业级文档管理后端系统
> 
> 🎯 **项目定位**: 简历面试项目，覆盖分片上传、MD5秒传、RBAC权限、版本管理、Kafka异步、接口幂等等核心后端场景
> 
> 💾 **存储方案**: 当前采用本地磁盘分片存储 (LocalFileStorageStrategy)，架构预留 StorageStrategy 接口，后续可无缝扩展对接对象存储 (如 MinIO / OSS / S3)

---

## 📋 目录

- [1. 项目架构](#1-项目架构)
- [2. 技术栈](#2-技术栈)
- [3. 功能清单](#3-功能清单)
- [4. 目录结构](#4-目录结构)
- [5. WSL 部署步骤](#5-wsl-部署步骤)
- [6. 接口文档](#6-接口文档)
- [7. 测试方式](#7-测试方式)
- [8. 工程优化与踩坑记录](#8-工程优化与踩坑记录)

---

## 1. 项目架构

```
┌─────────────────────────────────────────────────────────┐
│                   Controller (REST)                     │
│           Result统一封装 + GlobalExceptionHandler       │
├─────────────────────────────────────────────────────────┤
│              Interceptor (拦截器链)                      │
│   AuthInterceptor → PermissionInterceptor → Idempotent  │
├─────────────────────────────────────────────────────────┤
│                  Service (业务层)                        │
│   AuthService · FileUploadService · FileManageService  │
├─────────────────────────────────────────────────────────┤
│              StorageStrategy (存储策略接口)              │
│        LocalFileStorageStrategy (本地磁盘分片)          │
├─────────────────────────────────────────────────────────┤
│              Mapper (MyBatis-Plus)                      │
├─────────────────────────────────────────────────────────┤
│  MySQL8 │ Redis7 │ Kafka │ SkyWalking                  │
└─────────────────────────────────────────────────────────┘
```

### 核心业务流程

**分片上传流程**:
```
前端计算MD5 → 后端校验MD5是否已存在 → 存在→秒传复用
                               ↓ 不存在
           初始化分片 → 创建上传会话 → 返回分片大小和总片数
                               ↓
           循环上传分片 (每个分片独立请求) → 本地磁盘写入
                               ↓
           合并分片 → 文件合并到正式目录 → MD5完整性校验 → 落库
                               ↓
           异步任务: Kafka发送缩略图生成 + 访问日志
```

**权限校验流程**:
```
请求进入 → AuthInterceptor解析JWT → PermissionInterceptor
                                ↓
          Redis缓存权限 (key=doc:perm:user:{uid}:file:{fid}:{type})
                                ↓ 缓存未命中
          DB三级权限查询 (所有者 > 直接授权 > 角色授权)
                                ↓
          写回Redis → 返回结果
```

---

## 2. 技术栈

| 分类 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **语言** | Java | 21 | LTS版本 |
| **框架** | Spring Boot | 3.2.5 | 主框架 |
| **持久层** | MyBatis-Plus | 3.5.5 | ORM框架 |
| **连接池** | HikariCP | (内置) | 数据库连接池, 参数调优 |
| **数据库** | MySQL | 8.0 | 主存储 |
| **缓存** | Redis | 7.0 | 权限缓存/分片状态/幂等 |
| **对象存储** | 本地磁盘 | 分片存储 (策略模式预留扩展) |
| **消息队列** | Kafka | 最新(KRaft) | 异步任务/访问日志 |
| **链路追踪** | SkyWalking | 10.x | 全链路追踪 |
| **API文档** | Knife4j | 4.4.0 | OpenAPI3文档 |
| **工具** | Hutool | 5.8.26 | 通用工具 |
| **密码加密** | Spring Security Crypto | (内置) | BCrypt |

---

## 3. 功能清单

| # | 模块 | 功能 | 实现状态 |
|---|------|------|---------|
| 1 | 用户/RBAC | 用户、角色、权限三级模型 | ✅ |
| 2 | 文件权限 | 所有者>用户授权>角色授权 三级判断 | ✅ |
| 3 | 分片上传 | 初始化/上传分片/查询已上传/合并 | ✅ |
| 4 | 断点续传 | 查询已上传分片, 跳过重复分片 | ✅ |
| 5 | MD5秒传 | 相同MD5直接复用已有文件 | ✅ |
| 6 | 文件去重 | 物理存储复用, 相同MD5不重复存储 | ✅ |
| 7 | 版本管理 | 每次修改生成新版本, 支持回滚 | ✅ |
| 8 | 回收站 | 软删除30天/恢复/永久删除 | ✅ |
| 9 | Kafka异步 | 缩略图生成/访问日志落库 | ✅ |
| 10 | 死信队列 | 消费失败3次进入死信队列 | ✅ |
| 11 | 接口幂等 | 唯一业务编号保证写请求不重复 | ✅ |
| 12 | 缓存策略 | Redis缓存高频权限, 变更时主动失效 | ✅ |

---

## 4. 目录结构

```
docstorage/
├── docker-compose.yml                    # WSL一键启动中间件
├── pom.xml                               # Maven依赖
├── sql/
│   └── init.sql                          # 数据库初始化脚本
├── README.md
└── src/main/java/com/docstorage/
    ├── DocStorageApplication.java         # 启动类
    ├── common/
    │   ├── Result.java                   # 统一返回封装
    │   └── GlobalExceptionHandler.java   # 全局异常处理
    ├── config/                           # 配置类
    │   ├── MyBatisPlusConfig.java
    │   ├── RedisConfig.java
    │   ├── KafkaConfig.java
    │   ├── ThreadPoolConfig.java
    │   ├── WebConfig.java
    │   └── DocStorageHikariException.java
    ├── controller/                       # REST接口
    │   ├── AuthController.java
    │   ├── FileUploadController.java
    │   └── FileManageController.java
    ├── dto/                              # 入参/出参
    │   ├── req/                          # InitUploadReq, UploadPartReq...
    │   └── res/                          # InitUploadRes, FileInfoRes...
    ├── entity/                           # 数据库实体 (12张表)
    ├── enums/                            # 枚举 (FileStatus, PermType...)
    ├── exception/                        # 自定义异常
    ├── interceptor/                      # 拦截器
    │   ├── AuthInterceptor.java          # JWT认证
    │   ├── PermissionInterceptor.java    # 系统权限
    │   ├── IdempotentInterceptor.java    # 幂等校验
    │   └── InterceptorConfig.java         # 注册配置
    ├── mapper/                           # MyBatis-Plus Mapper
    ├── mq/                               # Kafka消费者
    │   ├── AccessLogConsumer.java
    │   ├── ThumbnailTaskConsumer.java
    │   └── DeadLetterConsumer.java
    ├── service/                          # 业务接口
    ├── service/impl/                     # 业务实现
    └── util/                             # 工具类
        ├── Md5Util.java                  # MD5流式计算
        ├── JwtUtil.java                  # JWT工具
        ├── KafkaProducerUtil.java         # Kafka生产者
        ├── RedisUtil.java                # Redis操作+分布式锁
        ├── StoragePathUtil.java           # 存储路径生成
        └── WebContextUtil.java            # Web上下文
```

---

## 5. WSL 部署步骤

### 5.1 启动中间件

```bash
# 1. 进入 WSL
wsl

# 2. 进入项目目录
cd /mnt/d/offer/docstorage

# 3. 启动所有中间件 (MySQL + Redis + Kafka)
docker compose up -d

# 4. 查看 WSL IP (重要! 配置文件需要用到)
hostname -I
# 示例输出: 172.31.100.50 192.168.0.1

# 5. 验证中间件状态
docker compose ps
```

### 5.2 配置项目

打开 `src/main/resources/application.yml`，将所有 `172.31.100.50` 替换为你实际的 WSL IP：

```yaml
spring:
  datasource:
    url: jdbc:mysql://<你的WSL_IP>:3306/docstorage?...
  data:
    redis:
      host: <你的WSL_IP>
  kafka:
    bootstrap-servers: <你的WSL_IP>:9092
```

### 5.3 导入数据库

```bash
# MySQL 容器初始化时会自动执行 sql/init.sql
# 如果需要手动执行:
docker exec -i doc-mysql mysql -uroot -proot123 docstorage < sql/init.sql
```

### 5.4 启动 SkyWalking (可选)

```bash
# 启动 SkyWalking (默认不启动, 使用 optional profile)
docker compose --profile optional up -d skywalking-oap skywalking-ui

# 下载 SkyWalking Java Agent
# https://skywalking.apache.org/downloads/
# 解压后启动命令添加:
# -javaagent:D:/skywalking/skywalking-agent.jar
# -Dskywalking.agent.service_name=docstorage-backend
# -Dskywalking.collector.backend_service=<你的WSL_IP>:11800
```

### 5.5 启动后端

```bash
# 方式一: IDEA 直接运行 DocStorageApplication.main()

# 方式二: 命令行
cd d:\offer\docstorage
mvn spring-boot:run

# 启动成功后访问:
# - Knife4j 接口文档: http://localhost:8080/api/doc.html
# - SkyWalking UI (如果启动): http://localhost:8081
```

---

## 6. 接口文档

启动后端后访问 `http://localhost:8080/api/doc.html` 查看完整 Knife4j 文档。

### 6.1 认证接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录, 返回 JWT Token |
| GET | `/api/auth/health` | 健康检查 |

### 6.2 分片上传接口

| 方法 | 路径 | 说明 | 幂等 |
|------|------|------|------|
| POST | `/api/file/upload/init` | **初始化分片上传** (含MD5秒传) | ✅ |
| POST | `/api/file/upload/part` | **上传单个分片** (multipart) | ✅ |
| GET | `/api/file/upload/parts?uploadId=xxx` | 查询已上传分片 (断点续传) | - |
| POST | `/api/file/upload/complete` | **合并所有分片** | ✅ |
| DELETE | `/api/file/upload/abort?uploadId=xxx` | 取消/清理分片 | ✅ |

### 6.3 文件管理接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/file/{fileId}` | 获取文件详情 | file:view |
| POST | `/api/file/permission` | 设置文件权限 | file:delete |
| DELETE | `/api/file/{fileId}` | 软删除 (进回收站) | file:delete |
| POST | `/api/file/{fileId}/recover` | 从回收站恢复 | 所有者 |
| DELETE | `/api/file/{fileId}/permanent` | 永久删除 | 所有者/管理员 |
| GET | `/api/file/{fileId}/versions` | 查看版本列表 | file:view |
| POST | `/api/file/version/rollback` | 版本回滚 | file:edit |
| GET | `/api/file/{fileId}/download-url` | 生成下载URL | file:download |

### 6.4 认证方式

所有业务接口需要在请求头携带 JWT Token：
```
Authorization: Bearer eyJhbGciOi...
X-Idempotent-Key: 550e8400-e29b-41d4-a716-446655440000   # 写请求必带
```

### 6.5 Apifox 测试流程

```
1. POST /api/auth/login
   Body: {"username":"admin","password":"admin123"}
   → 拿到 token

2. POST /api/file/upload/init
   Header: Authorization: Bearer {token}
   Header: X-Idempotent-Key: {uuid}
   Body: {fileCode, filename, fileMd5, fileSize}
   → 拿到 uploadId (或 instantUpload=true 秒传)

3. POST /api/file/upload/part
   Form-Data: file={分片文件}, uploadId, fileCode, partNumber, partSize

4. 重复步骤3直到所有分片上传完成

5. POST /api/file/upload/complete
   Body: {uploadId, fileCode, finalMd5}
   → 文件合并完成
```

---

## 7. 测试方式

### 7.1 JUnit 单元测试

```bash
cd d:\offer\docstorage
mvn test
```

已实现核心用例:
- ✅ `FileUploadServiceImplTest`: MD5秒传命中、正常初始化、幂等校验
- ✅ `FileManageServiceImplTest`: 所有者权限、缓存命中、缓存未命中、主动失效

### 7.2 Apifox Postman 脚本

```javascript
// 登录后自动设置全局变量
const resp = JSON.parse(responseBody);
if (resp.code === 200) {
    pm.collectionVariables.set("token", resp.data.token);
}
```

---

## 8. 工程优化与踩坑记录

> 💡 **面试高频考点**, 每个点都可以展开讲 5~10 分钟

### 8.1 分片并发合并冲突怎么处理?

**问题**: 前端可能因为网络重试、用户重复点击等原因，对同一个 uploadId 多次调用合并接口。

**解决方案** (三层防护):

```
1. 业务层状态机
   DocFile.fileStatus: 上传中(2) → 合并中(4) → 正常(1)
   合并前必须校验状态=上传中, 已合并则幂等返回
   
2. Redis 分布式锁
   lockKey = "doc:merge:lock:{uploadId}"
   SETNX + 过期时间, 120秒自动释放防死锁
   
3. 幂等拦截器
   X-Idempotent-Key 头, 唯一索引保证原子性
   重复请求直接返回之前的执行结果
```

**代码位置**: `FileUploadServiceImpl.completeUpload()`

### 8.2 MD5 大文件前端计算压力的取舍

**问题**: 前端计算 5GB 文件 MD5 可能耗时很久 (1-2分钟)，阻塞主线程。

**三种方案对比**:

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| 前端整体计算 | 简单 | OOM风险 >2GB文件 | 小文件 (<500MB) |
| 前端分片计算 | 分片MD5可校验 | 需要额外合并 | 有断点续传需求 |
| **流式计算(推荐)** | 内存友好, 不阻塞 | 需要 Worker | 通用 |

**推荐方案**: `SparkMD5` / `js-md5` (Worker + 流式读取)
- 使用 `FileReader` 分块读取 (如 2MB/块)
- 在 Web Worker 中计算, 不阻塞主线程
- 后端合并后再次计算 MD5 兜底校验防篡改

**代码位置**: `Md5Util.calculateStreamMd5()` (服务端兜底)

### 8.3 缓存击穿处理方案

**问题**: 热点文件权限 (如管理员可看所有) 缓存过期瞬间，大量请求穿透到 DB。

**解决方案**:

```
方案一: 互斥锁 + 逻辑过期 (推荐)
  - Redis SETNX 互斥锁, 只有一个线程去查DB
  - 查完回填缓存, 其他线程等待或返回旧值
  
方案二: 永不过期 + 异步刷新
  - 缓存不过期, 后台定时任务刷新
  - 适合非实时数据 (如用户基本信息)
  
方案三: 布隆过滤器 (防穿透)
  - 对于不存在的文件ID, 布隆过滤器直接拦截
  - 避免无效查询

当前项目实现:
  - 权限缓存 5分钟自然过期 + 变更时主动失效
  - Redis SETNX 分布式锁保护合并分片
```

**代码位置**: `RedisUtil.tryLock()`, `FileManageServiceImpl.setPermission()` (主动失效)

### 8.4 Kafka 消息丢失 / 重复消费的处理

**问题**: 分布式系统中消息丢失和重复消费是必然事件，必须设计防御机制。

**全链路保障**:

```
┌───────────── 生产端 ─────────────┐
│ acks=all (ISR全副本确认)          │
│ enable.idempotence=true (幂等)    │
│ retries=3 + retry-backoff         │
│ 发送失败 → 异常日志 + 死信队列     │
└──────────────────────────────────┘
          ↓ Broker
┌───────────── Broker端 ───────────┐
│ 副本数 >= 3                       │
│ unclean.leader.election.enable=false
│ retention.ms 合理设置              │
│ 死信队列 (消费失败3次转发)         │
└──────────────────────────────────┘
          ↓ 消费端
┌───────────── 消费端 ─────────────┐
│ enable-auto-commit=false (手动ACK)│
│ 业务成功后 ack.acknowledge()      │
│ DefaultErrorHandler 重试3次       │
│ 业务幂等 (唯一业务编号)            │
│ 进入死信队列 → 告警通知           │
└──────────────────────────────────┘
```

**当前配置**:
- 生产端: `acks=all + enable.idempotence=true`
- 消费端: `ack-mode=manual + DefaultErrorHandler(重试3次→死信)`
- 死信队列: `doc-dead-letter` + `DeadLetterConsumer` 监控

**代码位置**: `KafkaConfig.java`, `AccessLogConsumer.java`

### 8.5 权限缓存主动失效策略

**问题**: 用户权限变更后，如果只等缓存自然过期（5分钟），会出现"已经授权但还没权限"或"已经剥夺权限但还能访问"的短暂不一致。

**解决方案**: 写操作主动失效

```java
// 权限变更时批量清理相关缓存
String pattern = "doc:perm:user:*:file:" + fileId + "*";
redisUtil.deleteByPattern(pattern);

// 同时清理用户维度缓存
if (userId != null) {
    redisUtil.deleteByPattern("doc:perm:user:" + userId + ":*");
}
```

**失效范围**:
- 精确失效: 该文件的所有权限缓存 `doc:perm:*:file:{fileId}:*`
- 用户维度: 该用户的所有权限缓存 (用户角色变更时)
- 角色维度: 该角色下所有用户的缓存 (角色权限变更时)

**代价**: deleteByPattern 是 O(N) 操作，缓存 Key 不能过多（单模式下建议 <1000 个）

**代码位置**: `FileManageServiceImpl.setPermission()`

---

## 📞 技术支持

| 中间件 | 连接信息 |
|--------|---------|
| MySQL | `mysql -uroot -proot123 -h <WSL_IP> -P 3306 docstorage` |
| Redis | `redis-cli -h <WSL_IP> -p 6379` |
| Kafka | 消费组: `docstorage-group`, 死信队列: `doc-dead-letter` |
| SkyWalking UI | `http://localhost:8081` (需 --profile optional) |

