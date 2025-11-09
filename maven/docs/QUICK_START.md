# 🚀 Quick Start Guide

## 一键测试

```bash
# 构建 + 运行 + 测试
./verify-connection.sh
```

## 手动测试 (4 个终端)

### 终端 1️⃣: Demo Application
```bash
./run-demo.sh
```
输出: `Listening for transport dt_socket at address: 5006`

### 终端 2️⃣: Proxy Server
```bash
./run-proxy-server.sh
```
输出: `Debug Proxy Server started successfully!`

### 终端 3️⃣: Proxy Client
```bash
./run-proxy-client.sh
```
输出: `IDEA/Debugger can now connect to localhost:5005`

### 终端 4️⃣: JDI Debugger 或 IDEA

**选项 A: 使用 JDI Debugger**
```bash
./run-debugger.sh
```

**选项 B: 使用 IDEA**
1. `Run` → `Edit Configurations...` → `+` → `Remote JVM Debug`
2. Host: `localhost`, Port: `5005`
3. 在 `DemoApplication.java` 的 `processData()` 方法设置断点
4. 点击 Debug 🐛

## 端口映射

```
5005  → proxy-client  (本地监听,调试器连接这里)
8888  → proxy-server  (接收 client 连接)
5006  → demo-app      (目标应用的调试端口)
```

## 连接流程

```
调试器 (5005) → proxy-client → proxy-server (8888) → demo-app (5006)
    ↓             ↓               ↓                    ↓
  连接到        添加路由        解析路由              被调试
 localhost      参数           参数并转发
```

## 路由参数

在 proxy-client 启动时配置:
```bash
./run-proxy-client.sh <server-host> <server-port> <local-port> \
                      <podName> <targetHost> <targetPort>
```

示例:
```bash
./run-proxy-client.sh localhost 8888 5005 my-app-pod 10.0.1.50 5005
```

传递的参数:
- `podName`: my-app-pod
- `targetHost`: 10.0.1.50
- `targetPort`: 5005

## 验证成功

看到以下输出表示成功:

✅ **Demo App**: `Demo Application Started!`

✅ **Proxy Server**: 
```
Routing to pod: my-demo-pod at localhost:5006
Connected to target: localhost:5006
```

✅ **Proxy Client**:
```
Server acknowledged connection
```

✅ **Debugger**:
```
Successfully connected to target JVM!
✓ Breakpoint set at com.example.demo.DemoApplication.processData()
```

## 故障排查

❌ **端口占用**
```bash
lsof -i :5005 && kill <PID>
```

❌ **连接被拒绝**
- 检查启动顺序: demo-app → proxy-server → proxy-client → debugger

❌ **断点不命中**
- 确认应用以 `-agentlib:jdwp=...` 启动
- 检查断点位置的代码是否执行

## 清理

```bash
# 查找相关进程
ps aux | grep java | grep -E 'demo-app|proxy|debugger'

# 终止进程
pkill -f demo-app
pkill -f proxy-server
pkill -f proxy-client
pkill -f jdi-debugger
```

## 文档

- 📖 [README.md](../README.md) - 完整项目说明
- 📘 [USAGE.md](USAGE.md) - 详细使用指南
- 📊 [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) - 项目总结

## 命令速查

```bash
# 构建
mvn clean package

# 快速验证
./verify-connection.sh

# 完整测试
./test-all.sh

# 单独启动
./run-demo.sh
./run-proxy-server.sh 8888
./run-proxy-client.sh localhost 8888 5005 pod-name target-host 5006
./run-debugger.sh localhost 5005
```

## 下一步

1. ✅ 本地测试通过后
2. 🐳 构建 Docker 镜像
3. ☸️ 部署到 Kubernetes
4. 🔧 配置 IDEA Remote Debug
5. 🚀 开始远程调试!

