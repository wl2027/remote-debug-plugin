# WebSocket 版本远程调试方案

## 📖 概述

这是基于 [如何对线上服务进行远程调试](https://juejin.cn/post/7390340749579370548) 文章设计的 WebSocket 实现版本。

### 架构对比

#### 原始 TCP 版本
```
IDEA ←→ Proxy Client ←→ Proxy Server ←→ JVM
      (JDWP)        (TCP Socket)      (JDWP)
```

#### WebSocket 版本（推荐）
```
IDEA ←→ Proxy Client ←→ Proxy Server ←→ JVM
      (JDWP)        (WebSocket/HTTPS)  (JDWP)
```

### WebSocket 版本的优势

| 特性 | TCP 版本 | WebSocket 版本 |
|------|----------|----------------|
| **防火墙穿透** | ❌ 需要开放特殊端口 | ✅ 使用标准 HTTP/HTTPS |
| **加密传输** | ❌ 明文传输 | ✅ 支持 WSS (TLS) |
| **鉴权机制** | ❌ 无鉴权 | ✅ JWT/API Key |
| **审计日志** | ❌ 无 | ✅ 完整日志 |
| **生产就绪** | ⚠️ 仅测试环境 | ✅ 可用于生产 |

---

## 🚀 快速开始

### 前置条件

1. Java 11+
2. Maven 3.6+
3. 目标应用已启动 JDWP 调试端口

### 步骤 1: 编译项目

```bash
# 编译 Server
cd debug-proxy-server
mvn clean package

# 编译 Client
cd ../debug-proxy-client
mvn clean package
```

### 步骤 2: 生成认证 Token

```bash
cd debug-proxy-server
java -cp target/debug-proxy-server-1.0-SNAPSHOT.jar \
  com.example.proxy.server.TokenGenerator
```

选择 **API Key** 模式（简单测试用）：

```
选择 Token 类型:
1. JWT Token (推荐用于生产环境)
2. API Key (简单，适合开发/测试)
请输入选项 (1 或 2): 2

✅ API Key 生成成功！

API Key:
debug-key-1699123456789

使用方式:
  Authorization: ApiKey debug-key-1699123456789

命令行参数:
  ApiKey debug-key-1699123456789
```

### 步骤 3: 启动 Proxy Server（部署在集群内或有权访问目标 JVM 的环境）

```bash
# 设置 API Key（如果使用 API Key 模式）
export DEBUG_API_KEY=debug-key-1699123456789

# 启动 Server
java -cp target/debug-proxy-server-1.0-SNAPSHOT.jar \
  com.example.proxy.server.WebSocketDebugProxyServer \
  18888 \
  default-secret-key-change-me
```

参数说明：
- `18888`: WebSocket 监听端口
- `default-secret-key-change-me`: JWT 签名密钥（如果使用 JWT 模式）

### 步骤 4: 准备目标应用

确保目标 Java 应用已启动 JDWP 端口：

```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -jar your-app.jar
```

### 步骤 5: 启动 Proxy Client（开发者本地机器）

```bash
cd debug-proxy-client

java -cp target/debug-proxy-client-1.0-SNAPSHOT.jar \
  com.example.proxy.client.WebSocketDebugProxyClient \
  ws://proxy-server-host:18888 \
  "ApiKey debug-key-1699123456789" \
  target-app-host \
  5005 \
  15005 \
  my-app-pod
```

参数说明：
1. `ws://proxy-server-host:18888` - Proxy Server 的 WebSocket 地址
2. `"ApiKey debug-key-1699123456789"` - 认证 Token（注意引号）
3. `target-app-host` - 目标应用的主机名/IP
4. `5005` - 目标应用的 JDWP 端口
5. `15005` - 本地监听端口（IDEA 连接这个端口）
6. `my-app-pod` - Pod 名称（用于日志标识）

### 步骤 6: 配置 IDEA 远程调试

1. 打开 IDEA，选择 **Run** → **Edit Configurations**
2. 点击 **+** → **Remote JVM Debug**
3. 配置：
   - **Host**: `localhost`
   - **Port**: `15005`
   - **Debugger mode**: Attach to remote JVM
   - **Transport**: Socket
4. 点击 **Debug** 开始调试

---

## 🔐 安全配置

### JWT Token 模式（生产环境推荐）

#### 1. 生成 JWT Token

```bash
java -cp target/debug-proxy-server-1.0-SNAPSHOT.jar \
  com.example.proxy.server.TokenGenerator

# 选择选项 1，输入用户信息
```

#### 2. 启动 Server

```bash
java -cp target/debug-proxy-server-1.0-SNAPSHOT.jar \
  com.example.proxy.server.WebSocketDebugProxyServer \
  18888 \
  your-production-secret-key-at-least-32-chars
```

#### 3. 使用 JWT Token 连接

```bash
java -cp target/debug-proxy-client-1.0-SNAPSHOT.jar \
  com.example.proxy.client.WebSocketDebugProxyClient \
  wss://proxy-server:443 \
  "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  10.0.1.100 \
  5005
```

### 启用 HTTPS/WSS

#### 使用 Nginx 反向代理（推荐）

```nginx
server {
    listen 443 ssl http2;
    server_name debug-proxy.example.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;
    
    location /debug {
        proxy_pass http://localhost:18888;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        
        # 超时设置（调试会话可能很长）
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
```

客户端连接：

```bash
java -cp target/debug-proxy-client-1.0-SNAPSHOT.jar \
  com.example.proxy.client.WebSocketDebugProxyClient \
  wss://debug-proxy.example.com/debug \
  "Bearer YOUR_JWT_TOKEN" \
  10.0.1.100 \
  5005
```

---

## 🎯 高级用法

### 1. Kubernetes 环境调试

假设有一个 Kubernetes Pod 需要调试：

```bash
# 查看 Pod
kubectl get pods
# NAME                     READY   STATUS    RESTARTS   AGE
# my-app-7d9f8c6b5-abc12   1/1     Running   0          10m

# Pod 必须已启动 JDWP 端口 5005

# 启动 Client（通过 Service 名称访问）
java -cp target/debug-proxy-client-1.0-SNAPSHOT.jar \
  com.example.proxy.client.WebSocketDebugProxyClient \
  ws://debug-proxy-server.default.svc.cluster.local:18888 \
  "ApiKey debug-key-12345" \
  my-app-7d9f8c6b5-abc12.default.pod.cluster.local \
  5005 \
  15005 \
  my-app-7d9f8c6b5-abc12
```

### 2. 多实例并发调试

可以同时调试多个 Pod：

```bash
# 调试 Pod 1
java -cp client.jar WebSocketDebugProxyClient \
  ws://server:18888 "ApiKey key123" pod-1 5005 15001 pod-1 &

# 调试 Pod 2
java -cp client.jar WebSocketDebugProxyClient \
  ws://server:18888 "ApiKey key123" pod-2 5005 15002 pod-2 &

# IDEA 分别连接 localhost:15001 和 localhost:15002
```

### 3. 使用 Docker 部署

#### Server

```dockerfile
# debug-proxy-server/Dockerfile
FROM openjdk:11-jre-slim
WORKDIR /app
COPY target/debug-proxy-server-1.0-SNAPSHOT.jar app.jar
ENV DEBUG_API_KEY=change-me
EXPOSE 18888
CMD ["java", "-cp", "app.jar", "com.example.proxy.server.WebSocketDebugProxyServer", "18888", "your-secret-key"]
```

构建和运行：

```bash
cd debug-proxy-server
docker build -t debug-proxy-server:1.0 .

docker run -d \
  -p 18888:18888 \
  -e DEBUG_API_KEY=your-api-key \
  --name debug-proxy-server \
  debug-proxy-server:1.0
```

---

## 🐛 故障排查

### 1. 连接失败

**症状**: Client 无法连接到 Server

**检查**:
```bash
# 检查 Server 是否运行
netstat -an | grep 18888

# 测试 WebSocket 连接
curl -i -N \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Authorization: ApiKey debug-key-12345" \
  http://server:18888/
```

### 2. 认证失败

**症状**: 日志显示 "Authentication failed"

**解决**:
- 检查 Token 格式是否正确（注意 `Bearer` 或 `ApiKey` 前缀）
- JWT Token 是否过期
- Server 端的密钥是否匹配
- API Key 模式下，环境变量 `DEBUG_API_KEY` 是否设置

### 3. 调试断开

**症状**: 调试过程中突然断开

**可能原因**:
- 网络超时：增加 WebSocket 超时时间
- 目标 JVM 崩溃：检查应用日志
- 防火墙/代理中断：使用 WSS 并配置心跳

**心跳配置**（在 Server 中已默认启用 30 秒）：
```java
// Server 会自动发送 PING，Client 自动响应 PONG
```

---

## 📊 性能和限制

### 性能指标

| 指标 | 值 |
|------|-----|
| 并发连接数 | 默认 100（可调整） |
| 单连接限流 | 每用户最多 3 个会话 |
| 延迟增加 | ~10-50ms（相比直连） |
| 带宽消耗 | 与 JDWP 原始流量相同 |

### 已知限制

1. **Hot Swap 限制**：只能修改方法体，不能修改类结构
2. **网络依赖**：依赖稳定的网络连接
3. **安全风险**：务必在生产环境使用 WSS + JWT

---

## 🔄 与原始 TCP 版本对比

| 特性 | TCP 版本 | WebSocket 版本 |
|------|----------|----------------|
| **实现复杂度** | 简单 | 中等 |
| **网络兼容性** | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| **安全性** | ⭐ | ⭐⭐⭐⭐⭐ |
| **性能** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **生产环境** | ❌ | ✅ |
| **开发调试** | ✅ | ✅ |

---

## 📚 参考资料

- [原文: 如何对线上服务进行远程调试](https://juejin.cn/post/7390340749579370548)
- [JPDA 架构文档](https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/architecture.html)
- [JDWP 协议规范](https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html)
- [Java WebSocket API](https://github.com/TooTallNate/Java-WebSocket)

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License

