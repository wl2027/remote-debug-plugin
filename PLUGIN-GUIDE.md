# Remote Debug via WebSocket Proxy - IDEA插件使用指南

## 📋 插件简介

这是一个IDEA插件，支持通过WebSocket代理对远程Java应用进行调试。特别适用于需要跨越网络边界或防火墙的调试场景（如Kubernetes集群内的应用）。

### 核心特性

✅ **新的Run/Debug Configuration类型** - "Remote JVM Debug (WebSocket)"  
✅ **WebSocket隧道** - 通过WebSocket协议转发JDWP数据  
✅ **简单配置** - 类似原生Remote JVM Debug的配置方式  
✅ **自动重连** - 支持调试会话自动重启  
✅ **完全透明** - 对IDEA调试器完全透明，支持所有调试功能

---

## 🚀 快速开始

### 步骤 1: 安装插件

#### 方式 A: 从构建产物安装

```bash
# 编译插件
cd remote-debug-plugin
./gradlew buildPlugin

# 插件位置
ls build/distributions/remote-debug-plugin-*.zip
```

在IDEA中：
1. `File` → `Settings` → `Plugins`
2. 点击⚙️图标 → `Install Plugin from Disk...`
3. 选择 `build/distributions/remote-debug-plugin-*.zip`
4. 重启IDEA

#### 方式 B: 从源码运行（开发模式）

```bash
cd remote-debug-plugin
./gradlew runIde
```

### 步骤 2: 启动WebSocket代理服务器

```bash
cd debug-proxy-server
java -jar target/debug-proxy-server-1.0-SNAPSHOT.jar 18888
```

输出：
```
WebSocket Debug Proxy Server started on port 18888
Waiting for connections...
```

### 步骤 3: 启动目标应用（带JDWP）

```bash
cd demo-app
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -cp target/demo-app-1.0-SNAPSHOT.jar \
     com.example.demo.DemoApplication
```

### 步骤 4: 在IDEA中创建配置

1. 点击 `Run` → `Edit Configurations...`
2. 点击 `+` → 选择 **`Remote JVM Debug (WebSocket)`**
3. 配置参数：

```
Name: Debug My App via WS Proxy

WebSocket Server URL: ws://localhost:18888
Target Host: localhost
Target JDWP Port: 5005
Pod/Instance Name: my-app
Local Proxy Port: 15005
☐ Auto restart
```

4. 点击 `Apply` → `OK`

### 步骤 5: 开始调试

1. 在代码中设置断点
2. 点击 Debug 按钮（绿色虫子图标）
3. 选择刚创建的 "Debug My App via WS Proxy" 配置
4. 等待连接成功

**控制台输出示例**：
```
WebSocket Proxy Client starting...
Proxy Server: ws://localhost:18888
Target: localhost:5005
Local Port: 15005
Waiting for debugger connection on port 15005...
Debugger connected from: /127.0.0.1:xxxxx
Connecting to proxy server...
Connected to proxy server successfully!
Debug session established. Session ID: xxxx-xxxx-xxxx-xxxx
```

---

## 📊 架构说明

```
┌─────────────────────────────────────────────────────────────────┐
│                          调试流程                                │
└─────────────────────────────────────────────────────────────────┘

    IDEA Debugger                          Remote JVM
         │                                      │
         │ JDWP                                 │
         ▼                                      ▼
    ┌────────────┐                        ┌─────────┐
    │   Plugin   │                        │  App    │
    │  WsProxy   │                        │  :5005  │
    │  :15005    │                        │ (JDWP)  │
    └─────┬──────┘                        └────▲────┘
          │                                    │
          │ WebSocket                     JDWP │
          ▼                                    │
    ┌──────────────────────────────────────────┴──────┐
    │      WebSocket Proxy Server (:18888)            │
    │                                                  │
    │  • 接收WS连接                                    │
    │  • 建立JDWP连接                                  │
    │  • 双向转发数据                                  │
    └──────────────────────────────────────────────────┘
```

### 工作原理

1. **插件启动本地代理**：在指定端口（如15005）启动ServerSocket
2. **IDEA连接到插件**：IDEA调试器连接到localhost:15005
3. **插件建立WS连接**：连接到WebSocket代理服务器（如ws://localhost:18888）
4. **服务器连接目标**：代理服务器连接到目标JVM的JDWP端口（如5005）
5. **数据双向转发**：JDWP数据在IDEA和目标JVM之间透明转发

---

## ⚙️ 配置详解

### WebSocket Server URL

代理服务器的WebSocket地址。

**示例**：
- 本地开发：`ws://localhost:18888`
- 远程服务器：`ws://proxy.example.com:18888`
- HTTPS/WSS：`wss://secure-proxy.example.com/debug`

### Target Host

目标JVM所在的主机名或IP地址。

**示例**：
- 本地应用：`localhost`
- 远程服务器：`192.168.1.100`
- Kubernetes Pod：由代理服务器处理路由

### Target JDWP Port

目标JVM的JDWP监听端口。

**默认值**：`5005`

**如何设置**：启动Java应用时添加参数
```bash
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
```

### Pod/Instance Name

目标实例的标识符（可选）。用于在代理服务器日志中区分不同的调试会话。

### Local Proxy Port

插件本地代理监听的端口，IDEA调试器会连接到这个端口。

**默认值**：`15005`  
**注意**：确保该端口未被占用。

### Auto Restart

是否自动重启调试会话。

- **启用**：调试器断开后，插件会继续等待新的连接
- **禁用**：调试器断开后，插件停止运行

---

## 🎯 使用场景

### 场景 1: Kubernetes集群调试

**问题**：Pod运行在K8s集群内，无法直接访问JDWP端口

**解决方案**：
1. 在集群中部署 `debug-proxy-server` 作为Service
2. Pod启动时开启JDWP
3. 通过插件连接到代理服务器

```yaml
# proxy-server deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: debug-proxy-server
spec:
  template:
    spec:
      containers:
      - name: proxy
        image: debug-proxy-server:latest
        ports:
        - containerPort: 18888
---
apiVersion: v1
kind: Service
metadata:
  name: debug-proxy
spec:
  ports:
  - port: 18888
    targetPort: 18888
  selector:
    app: debug-proxy-server
```

**IDEA配置**：
```
WebSocket Server URL: ws://debug-proxy.cluster.local:18888
Target Host: my-app-pod
Target Port: 5005
```

### 场景 2: 跨防火墙调试

**问题**：目标应用在防火墙后，只能通过HTTPS访问

**解决方案**：
1. 在DMZ区部署 `debug-proxy-server`，配置HTTPS/WSS
2. 通过WSS协议加密连接

**IDEA配置**：
```
WebSocket Server URL: wss://proxy.company.com/debug
Target Host: app-server-internal
Target Port: 5005
```

### 场景 3: 多环境切换

**问题**：需要频繁切换不同环境进行调试

**解决方案**：
创建多个Run Configuration，快速切换

```
配置 1: Debug Dev Environment
  WS URL: ws://dev-proxy:18888
  Target: dev-app-server:5005

配置 2: Debug Staging Environment
  WS URL: ws://staging-proxy:18888
  Target: staging-app-server:5005

配置 3: Debug Production (只读)
  WS URL: wss://prod-proxy:18888
  Target: prod-app-server:5005
```

---

## 🔧 故障排查

### 问题 1: 插件安装后找不到配置类型

**症状**：`Run` → `Edit Configurations` 中没有 "Remote JVM Debug (WebSocket)"

**解决**：
1. 确认插件已启用：`Settings` → `Plugins` → 搜索 "Remote Debug"
2. 检查是否需要重启IDEA
3. 查看IDEA日志：`Help` → `Show Log in Finder/Explorer`

### 问题 2: 连接失败

**症状**：
```
Failed to connect to proxy server: Connection refused
```

**排查步骤**：
1. 确认代理服务器正在运行
   ```bash
   lsof -i :18888
   ```

2. 测试WebSocket连接
   ```bash
   curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" \
        -H "Sec-WebSocket-Version: 13" -H "Sec-WebSocket-Key: test" \
        http://localhost:18888
   ```

3. 检查防火墙规则
4. 验证URL格式（ws:// 或 wss://）

### 问题 3: 本地端口被占用

**症状**：
```
Failed to start proxy client: Address already in use
```

**解决**：
1. 查看占用端口的进程
   ```bash
   lsof -i :15005
   ```

2. 更改配置中的 "Local Proxy Port"
3. 或停止占用端口的进程

### 问题 4: 断点不生效

**原因**：
- 源码和运行的class文件不匹配
- Module classpath配置不正确

**解决**：
1. 确保代码已重新编译
2. 在配置中选择正确的Module
3. 清理并重新构建项目

### 问题 5: 调试会话意外断开

**可能原因**：
- 网络不稳定
- 目标应用退出
- 代理服务器重启

**排查**：
1. 查看插件控制台输出
2. 检查代理服务器日志
3. 验证目标应用是否还在运行

---

## 📝 日志和调试

### 查看插件日志

插件使用SLF4J记录日志，输出到IDEA控制台。

**启用详细日志**：
1. `Help` → `Diagnostic Tools` → `Debug Log Settings`
2. 添加：
   ```
   com.github.wl2027.remotedebugplugin
   ```

### 查看代理服务器日志

代理服务器日志包含详细的连接和转发信息。

```bash
# 启动时重定向日志
java -jar debug-proxy-server.jar 18888 > proxy-server.log 2>&1

# 实时查看
tail -f proxy-server.log
```

### 验证JDWP连接

测试目标应用的JDWP端口是否正常：

```bash
# 使用telnet测试
telnet localhost 5005

# 或使用nc
nc -zv localhost 5005
```

---

## 🎓 最佳实践

### 1. 安全性

- **生产环境**：使用WSS协议加密通信
- **认证**：在代理服务器实现认证机制
- **网络隔离**：通过VPN或专用网络连接

### 2. 性能优化

- **网络延迟**：选择地理位置接近的代理服务器
- **带宽**：确保足够的网络带宽
- **连接池**：代理服务器可复用JDWP连接

### 3. 开发工作流

```bash
# 1. 启动开发环境脚本
./start-all.sh

# 2. 在IDEA中设置断点

# 3. 点击Debug

# 4. 调试完成后停止
./stop-all.sh
```

### 4. 配置管理

将常用配置保存为项目的Run Configuration，提交到版本控制：

```
.idea/runConfigurations/Debug_via_WS_Proxy.xml
```

---

## 🔗 相关资源

- **源码仓库**：[GitHub](https://github.com/wl2027/remote-debug-plugin)
- **Issue追踪**：[GitHub Issues](https://github.com/wl2027/remote-debug-plugin/issues)
- **JDWP协议**：[官方文档](https://docs.oracle.com/en/java/javase/11/docs/specs/jdwp/jdwp-spec.html)
- **IntelliJ平台SDK**：[文档](https://plugins.jetbrains.com/docs/intellij/)

---

## 💡 提示和技巧

### 快捷键

- **开始调试**：`Shift + F9`（选择配置）
- **设置断点**：`Cmd/Ctrl + F8`
- **单步执行**：`F8`
- **步入**：`F7`
- **继续执行**：`F9`

### 调试技巧

1. **条件断点**：右键断点 → 设置条件
2. **日志断点**：不暂停，只输出日志
3. **远程表达式求值**：`Alt + F8`
4. **强制返回**：`Cmd/Ctrl + Shift + F8`

### 配置模板

创建配置模板供团队成员使用：
1. 配置好一个Run Configuration
2. 右键 → `Save as Template`
3. 分享给团队成员

---

## 🤝 贡献

欢迎贡献代码、报告Bug或提出改进建议！

**开发环境设置**：
```bash
git clone https://github.com/wl2027/remote-debug-plugin.git
cd remote-debug-plugin
./gradlew runIde
```

**运行测试**：
```bash
./gradlew test
```

**构建发布版本**：
```bash
./gradlew buildPlugin
```

---

## 📄 许可证

Apache License 2.0

---

**Happy Debugging! 🐛✨**

