# 🚀 快速开始指南

## 10 分钟上手 WebSocket 远程调试

### 前置准备

1. **Java 11+** 和 **Maven 3.6+**
2. 一个需要调试的 Java 应用（已启动 JDWP）

---

## 步骤 1: 克隆并编译（2 分钟）

```bash
# 进入项目目录
cd proxy-debug

# 编译 Server 和 Client
mvn clean package -pl debug-proxy-server,debug-proxy-client
```

---

## 步骤 2: 准备测试应用（1 分钟）

如果你还没有测试应用，可以创建一个简单的：

```java
// TestApp.java
public class TestApp {
    public static void main(String[] args) throws Exception {
        System.out.println("测试应用启动...");
        while (true) {
            System.out.println("当前时间: " + System.currentTimeMillis());
            Thread.sleep(5000);
        }
    }
}
```

以调试模式启动：

```bash
javac TestApp.java
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 TestApp

# 输出:
# Listening for transport dt_socket at address: 5005
# 测试应用启动...
```

---

## 步骤 3: 启动 Proxy Server（1 分钟）

在**第一个终端**：

```bash
./start-websocket-server.sh

# 或手动执行：
cd debug-proxy-server
export DEBUG_API_KEY=debug-key-12345
java -cp target/debug-proxy-server-1.0-SNAPSHOT.jar \
  com.example.proxy.server.WebSocketDebugProxyServer \
  18888 \
  default-secret-key
```

看到以下输出表示成功：

```
WebSocket Debug Proxy Server started on port 18888
Press Ctrl+C to stop the server
```

---

## 步骤 4: 启动 Proxy Client（1 分钟）

在**第二个终端**：

```bash
./start-websocket-client.sh \
  ws://localhost:18888 \
  localhost \
  5005

# 或手动执行：
cd debug-proxy-client
java -cp target/debug-proxy-client-1.0-SNAPSHOT.jar \
  com.example.proxy.client.WebSocketDebugProxyClient \
  ws://localhost:18888 \
  "ApiKey debug-key-12345" \
  localhost \
  5005 \
  15005 \
  test-app
```

看到以下输出表示成功：

```
Local JDWP server started on port 15005
IDEA/Debugger can now connect to localhost:15005
```

---

## 步骤 5: 配置 IDEA 调试（3 分钟）

### 5.1 创建远程调试配置

1. 打开 IDEA
2. 点击 **Run** → **Edit Configurations**
3. 点击 **+** 号 → 选择 **Remote JVM Debug**
4. 配置如下：

```
Name: WebSocket Remote Debug
Debugger mode: Attach to remote JVM
Transport: Socket
Host: localhost
Port: 15005
Command line arguments for remote JVM: (忽略这个，不需要)
```

5. 点击 **Apply** → **OK**

### 5.2 设置断点

打开你的源码文件（例如 `TestApp.java`），在以下行设置断点：

```java
System.out.println("当前时间: " + System.currentTimeMillis());
```

### 5.3 开始调试

1. 点击 **Debug** 按钮（绿色虫子图标）
2. 选择 **WebSocket Remote Debug**
3. 等待连接成功

---

## 步骤 6: 验证调试功能（2 分钟）

### 断点测试

如果一切正常，你应该看到：

1. **IDEA** 在断点处暂停
2. **Variables** 窗口显示变量值
3. 可以单步执行（F8）、继续执行（F9）等

### 查看连接日志

**Proxy Client 终端**：
```
Session xxx: Connecting to proxy server...
Session xxx: Connected to proxy server
Session xxx: Debug session established
```

**Proxy Server 终端**：
```
New WebSocket connection from: /127.0.0.1:xxxxx
Session xxx: Connecting to pod 'test-app' at localhost:5005
Session xxx: Connected to target JVM
Session xxx: Debug session established successfully
```

---

## 🎉 完成！

恭喜！你已经成功配置了 WebSocket 远程调试。

---

## 常见问题

### ❓ 连接失败

**症状**：IDEA 显示 "Unable to open debugger port"

**解决**：
1. 检查 Client 是否成功启动（应显示 "Local JDWP server started"）
2. 检查端口是否被占用：`netstat -an | grep 15005`
3. 尝试使用不同端口

### ❓ 认证失败

**症状**：Server 日志显示 "Authentication failed"

**解决**：
1. 确认 Server 和 Client 使用相同的 API Key
2. 检查环境变量：`echo $DEBUG_API_KEY`
3. 确认 Token 格式正确：`"ApiKey debug-key-12345"` （注意引号和前缀）

### ❓ 无法连接到目标应用

**症状**：Server 日志显示 "Error connecting to target"

**解决**：
1. 确认目标应用已启动 JDWP：`netstat -an | grep 5005`
2. 检查主机名/IP 是否正确
3. 检查端口是否正确
4. 尝试手动连接：`telnet localhost 5005`

---

## 下一步

### 🔐 启用安全模式

生成 JWT Token：

```bash
cd debug-proxy-server
java -cp target/debug-proxy-server-1.0-SNAPSHOT.jar \
  com.example.proxy.server.TokenGenerator
```

使用 JWT Token 连接：

```bash
./start-websocket-client.sh \
  ws://localhost:18888 \
  localhost \
  5005 \
  15005 \
  test-app \
  "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### 🌐 远程环境调试

修改 Client 命令，指向远程 Server：

```bash
./start-websocket-client.sh \
  wss://debug-proxy.example.com \
  10.0.1.100 \
  5005 \
  15005 \
  production-app \
  "Bearer YOUR_JWT_TOKEN"
```

### 📚 深入学习

- 查看 [完整文档](./WEBSOCKET-GUIDE.md)
- 查看 [架构对比](./ARCHITECTURE-COMPARISON.md)
- 阅读 [原文](https://juejin.cn/post/7390340749579370548)

---

## 脚本一键启动

### 本地测试场景

```bash
# 终端 1: 启动测试应用
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 TestApp

# 终端 2: 启动 Server
./start-websocket-server.sh

# 终端 3: 启动 Client
./start-websocket-client.sh ws://localhost:18888 localhost 5005

# IDEA: 连接 localhost:15005
```

### Kubernetes 场景

```bash
# 获取 Pod IP
POD_IP=$(kubectl get pod my-app-pod -o jsonpath='{.status.podIP}')

# 启动 Client（假设 Server 已部署在集群内）
./start-websocket-client.sh \
  ws://debug-proxy-server.default.svc.cluster.local:18888 \
  $POD_IP \
  5005 \
  15005 \
  my-app-pod \
  "Bearer YOUR_TOKEN"
```

---

**需要帮助？** 查看 [完整文档](./WEBSOCKET-GUIDE.md) 或提交 Issue。

