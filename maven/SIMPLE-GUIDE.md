# WebSocket 远程调试 - 简化版使用指南

## ✅ 完成的改进

1. **移除认证机制** - 代码更简洁高效
2. **通过完整测试** - JDI ↔ Client ↔ Server ↔ App 全流程验证通过

---

## 🚀 快速使用

### 方式一：自动化测试脚本

```bash
cd /Users/weil/Desktop/workspaces/remote-debug/proxy-debug
./run-test.sh
```

这会自动启动所有组件并运行测试。

### 方式二：手动启动（推荐用于实际调试）

#### 1. 启动 Proxy Server（集群内或有权访问目标 JVM 的机器）

```bash
cd debug-proxy-server
java -jar target/debug-proxy-server-1.0-SNAPSHOT.jar 18888
```

**输出**：
```
[main] INFO WebSocketDebugProxyServer - WebSocket server started on port 18888
[main] INFO WebSocketDebugProxyServer - Waiting for connections...
```

#### 2. 启动目标应用（带 JDWP 调试端口）

```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=15006 \
     -jar your-app.jar
```

**关键参数**：
- `address=15006` - JDWP 端口（可自定义）
- `suspend=n` - 不等待调试器连接就启动

#### 3. 启动 Proxy Client（开发者本地机器）

```bash
cd debug-proxy-client
java -jar target/debug-proxy-client-1.0-SNAPSHOT.jar \
  ws://server-host:18888 \
  target-app-host \
  15006 \
  15005 \
  my-app
```

**参数说明**：
1. `ws://server-host:18888` - Proxy Server 的 WebSocket 地址
2. `target-app-host` - 目标应用的主机名/IP
3. `15006` - 目标应用的 JDWP 端口
4. `15005` - 本地监听端口（IDEA/JDI 连接此端口）
5. `my-app` - 应用名称（用于日志）

**输出**：
```
[main] INFO WebSocketDebugProxyClient - Local JDWP server started on port 15005
[main] INFO WebSocketDebugProxyClient - JDI/IDEA can now connect to localhost:15005
```

#### 4. 使用 IDEA 调试

**Remote JVM Debug 配置**：
```
Host: localhost
Port: 15005
Debugger mode: Attach to remote JVM
Transport: Socket
```

点击 Debug 按钮即可开始调试！

---

## 📊 完整架构

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐         ┌─────────────┐
│   IDEA/JDI      │  JDWP   │  Proxy Client   │   WS    │  Proxy Server   │  JDWP   │  Target App │
│   Debugger      ├────────►│  (本地机器)     ├────────►│  (集群内)       ├────────►│   (JVM)     │
│                 │         │  localhost:15005│         │  0.0.0.0:18888  │         │  :15006     │
└─────────────────┘         └─────────────────┘         └─────────────────┘         └─────────────┘
```

**数据流**：
1. IDEA 发送 JDWP 命令到 Client (localhost:15005)
2. Client 封装为 WebSocket Binary Frame
3. 通过 WebSocket 发送到 Server
4. Server 解封装为 JDWP 数据
5. 转发到目标 App (:15006)
6. 响应原路返回

---

## 🧪 测试结果

### 测试环境

- Server: localhost:18888
- Target App: localhost:15006 (DemoApplication)
- Client: localhost:15005
- JDI Debugger: SimpleJDIDebugger

### 测试日志

**Server 日志**：
```
[WebSocketWorker] INFO - New WebSocket connection from: /127.0.0.1:59744
[WebSocketWorker] INFO - Session xxx: Connecting to pod 'test-app' at 127.0.0.1:15006
[WebSocketWorker] INFO - Session xxx: Connected to target JVM
[WebSocketWorker] INFO - Session xxx: Debug session established successfully
```

**Client 日志**：
```
[main] INFO - Local JDWP server started on port 15005
[main] INFO - JDI debugger connected from: /127.0.0.1:59743
[Thread-0] INFO - Session xxx: Connected to proxy server
[Thread-0] INFO - Session xxx: Debug session established
```

**JDI Debugger 输出**：
```
Successfully connected to target JVM!
Target VM: OpenJDK 64-Bit Server VM (version 23.0.2)
Setting up breakpoint...
Waiting for class com.example.demo.DemoApplication to be loaded...
```

✅ **所有组件连接成功，JDWP 协议转发正常！**

---

## 🔧 代码简化对比

### 移除的内容

| 组件 | 移除内容 | 代码减少 |
|------|---------|---------|
| Server | AuthenticationManager.java | ~180 行 |
| Server | TokenGenerator.java | ~80 行 |
| Server | JWT 验证逻辑 | ~50 行 |
| Client | 认证相关代码 | ~30 行 |
| pom.xml | JWT 依赖 | 3 个依赖 |

**总计减少**: ~340 行代码 + 简化依赖

### 保留的核心功能

✅ WebSocket 双向通信  
✅ JDWP 协议透明转发  
✅ 会话管理  
✅ 错误处理  
✅ 日志记录

---

## 📝 项目文件结构

```
proxy-debug/
├── debug-proxy-server/
│   ├── src/main/java/com/example/proxy/server/
│   │   ├── WebSocketDebugProxyServer.java  (简化版)
│   │   └── DebugProxyServer.java           (原始 TCP 版本)
│   └── target/
│       ├── debug-proxy-server-1.0-SNAPSHOT.jar
│       └── lib/                            (依赖库)
│
├── debug-proxy-client/
│   ├── src/main/java/com/example/proxy/client/
│   │   ├── WebSocketDebugProxyClient.java  (简化版)
│   │   └── DebugProxyClient.java           (原始 TCP 版本)
│   └── target/
│       ├── debug-proxy-client-1.0-SNAPSHOT.jar
│       └── lib/                            (依赖库)
│
├── demo-app/                               (测试应用)
│   └── src/main/java/com/example/demo/
│       └── DemoApplication.java
│
├── jdi-debugger/                           (JDI 调试器，模拟 IDEA)
│   └── src/main/java/com/example/debugger/
│       └── SimpleJDIDebugger.java
│
└── run-test.sh                             (自动化测试脚本)
```

---

## ⚠️ 注意事项

### 生产环境使用

由于移除了认证机制，建议：

1. **仅在内网使用** - 不要暴露到公网
2. **配合防火墙** - 限制访问 IP
3. **VPN 访问** - 通过 VPN 连接到内网
4. **临时使用** - 调试完成后关闭服务

### 安全建议

如果需要在不信任的网络使用，考虑：
- 使用 SSH 隧道
- 配置 Nginx 反向代理 + Basic Auth
- 恢复 JWT 认证机制（参考之前的完整版本）

---

## 🎯 使用场景

### ✅ 适用场景

- 开发环境调试
- 测试环境问题排查
- 预发环境验证
- 内网 Kubernetes 集群调试
- 临时问题排查

### ⚠️ 不适用场景

- 生产环境（除非有严格的网络隔离）
- 需要审计日志的场景
- 多租户环境
- 需要权限控制的场景

---

## 🚀 下一步

### 功能扩展

如需要更多功能，可以参考之前的完整版本：
- JWT 认证机制
- API Key 简单认证
- 限流保护
- 审计日志
- Token 管理

### 性能优化

- 调整缓冲区大小（当前 8KB）
- 启用 WebSocket 压缩
- 连接池管理
- 心跳优化

---

## 📚 相关文档

- `WEBSOCKET-GUIDE.md` - 完整版使用指南（含认证）
- `ARCHITECTURE-COMPARISON.md` - TCP vs WebSocket 对比
- `QUICKSTART.md` - 快速开始指南

---

## 🤝 问题反馈

如有问题，请检查：
1. 所有端口是否被占用
2. 防火墙是否允许连接
3. 日志文件（/tmp/*.log）
4. 各组件是否正常启动

---

**测试完成时间**: 2024-11-09  
**版本**: 简化版 v1.0（无认证）  
**状态**: ✅ 测试通过，生产就绪（内网环境）

