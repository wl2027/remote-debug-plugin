# 系统架构详解

## 总体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              开发者本地环境                                    │
│                                                                               │
│  ┌─────────────────────────┐                                                 │
│  │   IntelliJ IDEA         │                                                 │
│  │   或 JDI Debugger       │                                                 │
│  │                         │                                                 │
│  │  - 设置断点              │                                                 │
│  │  - 查看变量              │                                                 │
│  │  - 单步调试              │                                                 │
│  └───────────┬─────────────┘                                                 │
│              │ JDWP Protocol                                                 │
│              │ (Java Debug Wire Protocol)                                    │
│              ▼                                                                │
│  ┌─────────────────────────────────────────────────────────┐                │
│  │  debug-proxy-client (本地代理客户端)                     │                │
│  │                                                          │                │
│  │  监听端口: localhost:5005                                │                │
│  │                                                          │                │
│  │  主要功能:                                               │                │
│  │  1️⃣  接收来自 IDEA 的调试连接                            │                │
│  │  2️⃣  添加自定义路由参数:                                 │                │
│  │     • podName: 目标 Pod 名称                            │                │
│  │     • targetHost: 目标主机地址                          │                │
│  │     • targetPort: 目标调试端口                          │                │
│  │  3️⃣  连接到远程 proxy-server                            │                │
│  │  4️⃣  透明转发 JDWP 流量                                 │                │
│  │                                                          │                │
│  └───────────┬──────────────────────────────────────────────┘                │
│              │                                                                │
└──────────────┼────────────────────────────────────────────────────────────────┘
               │ Custom Protocol:
               │ 1. Header: "X-DEBUG-ROUTE"
               │ 2. Params: {podName, targetHost, targetPort}
               │ 3. JDWP Traffic (bidirectional)
               │
               │ 通过网络 (Internet/VPN)
               │
┌──────────────▼────────────────────────────────────────────────────────────────┐
│                           Kubernetes 集群内                                    │
│                                                                                │
│  ┌──────────────────────────────────────────────────────────┐                │
│  │  debug-proxy-server (集群内代理服务器)                    │                │
│  │                                                           │                │
│  │  监听端口: 8888 (通过 NodePort 30888 暴露)                │                │
│  │                                                           │                │
│  │  主要功能:                                                │                │
│  │  1️⃣  接收来自 proxy-client 的连接                         │                │
│  │  2️⃣  解析自定义协议头 "X-DEBUG-ROUTE"                     │                │
│  │  3️⃣  提取路由参数:                                        │                │
│  │     • podName = "my-app-xyz"                            │                │
│  │     • targetHost = "10.244.0.5"                         │                │
│  │     • targetPort = "5005"                               │                │
│  │  4️⃣  根据参数路由到目标 Pod                               │                │
│  │  5️⃣  建立到目标 Pod 的 JDWP 连接                          │                │
│  │  6️⃣  双向透明转发调试流量                                 │                │
│  │                                                           │                │
│  └───────────┬───────────────────────────────────────────────┘                │
│              │ JDWP Protocol                                                  │
│              │ (直接连接 Pod IP)                                              │
│              ▼                                                                │
│  ┌──────────────────────────────────────────────────────────┐                │
│  │  Target Pod (目标应用 Pod)                                │                │
│  │                                                           │                │
│  │  例如: my-app-xyz-12345                                   │                │
│  │                                                           │                │
│  │  ┌─────────────────────────────────────────────┐         │                │
│  │  │  Application Container                      │         │                │
│  │  │                                             │         │                │
│  │  │  启动参数:                                   │         │                │
│  │  │  -agentlib:jdwp=                            │         │                │
│  │  │    transport=dt_socket,                     │         │                │
│  │  │    server=y,                                │         │                │
│  │  │    suspend=n,                               │         │                │
│  │  │    address=*:5005                           │         │                │
│  │  │                                             │         │                │
│  │  │  应用代码:                                   │         │                │
│  │  │  • 业务逻辑                                  │         │                │
│  │  │  • 可设置断点                                │         │                │
│  │  │  • 可查看变量                                │         │                │
│  │  │  • 可单步执行                                │         │                │
│  │  │                                             │         │                │
│  │  └─────────────────────────────────────────────┘         │                │
│  │                                                           │                │
│  └───────────────────────────────────────────────────────────┘                │
│                                                                                │
└────────────────────────────────────────────────────────────────────────────────┘
```

## 数据流详解

### 阶段 1: 连接建立

```sequence
IDEA           ProxyClient      ProxyServer      TargetPod
  │                │                │                │
  │──JDWP Connect──>│                │                │
  │                │                │                │
  │                │──TCP Connect──>│                │
  │                │                │                │
  │                │──Send Header──>│                │
  │                │ "X-DEBUG-ROUTE"│                │
  │                │                │                │
  │                │──Send Params──>│                │
  │                │  podName       │                │
  │                │  targetHost    │                │
  │                │  targetPort    │                │
  │                │                │                │
  │                │                │──Parse Params──│
  │                │                │                │
  │                │                │──JDWP Connect─>│
  │                │                │                │
  │                │<─────"OK"──────│                │
  │                │                │                │
  │<──Connected────│                │                │
```

### 阶段 2: 调试会话

```sequence
IDEA           ProxyClient      ProxyServer      TargetPod
  │                │                │                │
  │──Set BP──────>│──────────────>│──────────────>│
  │                │                │                │
  │<─────ACK───────│<──────────────│<──────────────│
  │                │                │                │
  │                │                │                │  [执行到断点]
  │                │                │                │
  │<───BP Hit──────│<──────────────│<──────────────│
  │                │                │                │
  │──Get Vars────>│──────────────>│──────────────>│
  │<───Vars────────│<──────────────│<──────────────│
  │                │                │                │
  │──Step Over───>│──────────────>│──────────────>│
  │                │                │                │
  │──Resume──────>│──────────────>│──────────────>│
  │                │                │                │
```

## 协议详解

### Custom Routing Protocol

#### 1. Header Phase (自定义协议头)
```
Client → Server: "X-DEBUG-ROUTE" (UTF String)
```

#### 2. Parameter Phase (参数传递)
```
Client → Server: N (int, 参数数量)

For i = 1 to N:
    Client → Server: key_i (UTF String)
    Client → Server: value_i (UTF String)
```

#### 3. Acknowledgment Phase (确认)
```
Server → Client: "OK" (UTF String)
```

#### 4. JDWP Phase (调试协议)
```
Client ↔ Server: JDWP Protocol (transparent bidirectional)
```

### 协议示例

```
[Client → Server]
"X-DEBUG-ROUTE"              ← Header
3                            ← Param count
"podName"                    ← Key 1
"my-app-xyz-12345"          ← Value 1
"targetHost"                 ← Key 2
"10.244.0.5"                ← Value 2
"targetPort"                 ← Key 3
"5005"                       ← Value 3

[Server → Client]
"OK"                         ← Acknowledgment

[Bidirectional]
<JDWP Traffic>              ← Debug protocol
```

## 核心类设计

### 1. DebugProxyClient

```java
public class DebugProxyClient {
    private int localPort;              // 本地监听端口 (5005)
    private String serverHost;          // Proxy Server 地址
    private int serverPort;             // Proxy Server 端口 (8888)
    private Map<String, String> routingParams;  // 路由参数
    
    // 添加路由参数
    public void addRoutingParam(String key, String value);
    
    // 启动监听
    public void start();
    
    // 内部类: 处理每个连接
    class ConnectionHandler implements Runnable {
        // 发送路由信息
        private void sendRoutingInfo(Socket serverSocket);
        
        // 双向转发
        private static void forward(Socket from, Socket to);
    }
}
```

### 2. DebugProxyServer

```java
public class DebugProxyServer {
    private int port;                   // 监听端口 (8888)
    
    // 启动服务器
    public void start();
    
    // 内部类: 处理每个连接
    class ConnectionHandler implements Runnable {
        // 解析路由信息
        private Map<String, String> parseRoutingInfo(Socket socket);
        
        // 双向转发
        private static void forward(Socket from, Socket to);
    }
}
```

### 3. SimpleJDIDebugger

```java
public class SimpleJDIDebugger {
    private VirtualMachine vm;          // JVM 虚拟机引用
    
    // 连接到目标 JVM
    public void connect(String host, int port);
    
    // 设置断点
    public void setBreakpoint();
    
    // 事件循环
    public void eventLoop();
}
```

### 4. DemoApplication

```java
public class DemoApplication {
    // 主方法
    public static void main(String[] args);
    
    // 运行循环
    public void run();
    
    // 处理数据 (断点设置在这里)
    private void processData();
    
    // 生成数据
    private List<String> generateData();
    
    // 处理项目
    private String processItems(List<String> items);
}
```

## 部署架构

### 开发环境

```
┌─────────────────────────────────────┐
│  Developer Laptop                   │
│                                     │
│  ┌─────────┐  ┌──────────────────┐ │
│  │  IDEA   │  │ proxy-client     │ │
│  │  :5005  │←─│ localhost:5005   │ │
│  └─────────┘  └──────────────────┘ │
│                     │               │
│                     ▼               │
│  ┌──────────────────────────────┐  │
│  │ proxy-server  localhost:8888 │  │
│  └──────────────────────────────┘  │
│                     │               │
│                     ▼               │
│  ┌──────────────────────────────┐  │
│  │ demo-app      localhost:5006 │  │
│  └──────────────────────────────┘  │
└─────────────────────────────────────┘
```

### 生产环境

```
┌──────────────────────────┐         ┌────────────────────────────────┐
│  Developer Laptop        │         │  Kubernetes Cluster            │
│                          │         │                                │
│  ┌─────────┐            │         │  ┌──────────────────────────┐  │
│  │  IDEA   │            │         │  │  debug-proxy-server      │  │
│  │  :5005  │            │         │  │  NodePort: 30888         │  │
│  └────┬────┘            │         │  │  ClusterIP: 10.0.0.50    │  │
│       │                 │         │  └────────┬─────────────────┘  │
│       ▼                 │         │           │                    │
│  ┌──────────────────┐   │         │           ▼                    │
│  │ proxy-client     │   │         │  ┌────────────────────────┐   │
│  │ localhost:5005   │───┼─────────┼─>│ Pods (my-app-*)       │   │
│  └──────────────────┘   │  VPN/   │  │ • my-app-xyz-12345    │   │
│                          │  公网   │  │   IP: 10.244.0.5      │   │
└──────────────────────────┘         │  │   Debug Port: 5005    │   │
                                     │  └────────────────────────┘   │
                                     └────────────────────────────────┘
```

## 网络流量分析

### 端口使用

| 端口 | 组件 | 用途 | 访问方式 |
|-----|------|------|---------|
| 5005 | proxy-client | 接收 IDEA 连接 | localhost |
| 5006 | demo-app | JDWP 调试端口 | localhost/Pod IP |
| 8888 | proxy-server | 接收 client 连接 | ClusterIP |
| 30888 | proxy-server | NodePort 暴露 | Node IP |

### 流量特征

| 阶段 | 流量类型 | 大小 | 延迟要求 |
|-----|---------|------|---------|
| 连接建立 | 自定义协议 | < 1KB | 低 |
| 参数传递 | 文本 | < 500B | 低 |
| 断点命令 | JDWP | ~100B | 高 (实时) |
| 变量查询 | JDWP | ~1-10KB | 中 |
| 代码执行 | JDWP | ~100B | 高 (实时) |

## 安全架构

### 威胁模型

```
┌─────────────────────────────────────────────────────────┐
│  潜在威胁                                                │
│                                                          │
│  1. 未授权访问  →  添加认证机制                          │
│  2. 中间人攻击  →  使用 TLS/SSL                          │
│  3. 参数注入    →  参数验证和清理                        │
│  4. 资源耗尽    →  连接数限制、超时控制                  │
│  5. 信息泄露    →  审计日志、访问控制                    │
└─────────────────────────────────────────────────────────┘
```

### 安全增强方案

```
┌─────────────────────────────────────────────────────────┐
│  Layer 1: 网络层                                         │
│  • NetworkPolicy 限制流量                                │
│  • 只允许特定 IP 访问                                    │
└───────────────────┬─────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────┐
│  Layer 2: 传输层                                         │
│  • TLS 1.3 加密                                          │
│  • 证书双向认证                                          │
└───────────────────┬─────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────┐
│  Layer 3: 应用层                                         │
│  • JWT Token 认证                                        │
│  • 参数白名单验证                                        │
│  • 速率限制                                              │
└───────────────────┬─────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────┐
│  Layer 4: 审计层                                         │
│  • 所有连接记录日志                                      │
│  • 异常行为告警                                          │
│  • 定期安全审计                                          │
└─────────────────────────────────────────────────────────┘
```

## 性能优化

### 当前实现

```
单线程转发 → 每连接 2 线程
8KB 缓冲区 → 标准大小
阻塞 I/O   → 简单可靠
```

### 优化建议

```
1. 增加缓冲区
   8KB → 64KB (大数据传输)

2. 使用 NIO
   阻塞 I/O → 非阻塞 I/O (高并发)

3. 连接池
   每次新建 → 复用连接 (频繁调试)

4. 压缩传输
   原始数据 → GZIP 压缩 (慢网络)
```

## 监控指标

### 关键指标

```
┌────────────────────────────────────────┐
│  Metrics                               │
│                                        │
│  • 活跃连接数                          │
│  • 连接建立延迟                        │
│  • 数据传输速率                        │
│  • 错误率                              │
│  • 平均会话时长                        │
└────────────────────────────────────────┘
```

### Prometheus 指标示例

```
# 活跃连接数
debug_proxy_connections_active{component="server"} 5

# 总连接数
debug_proxy_connections_total{component="server"} 123

# 数据传输量
debug_proxy_bytes_transferred{direction="sent"} 1048576
debug_proxy_bytes_transferred{direction="received"} 524288

# 连接延迟
debug_proxy_connection_duration_seconds{quantile="0.5"} 0.05
debug_proxy_connection_duration_seconds{quantile="0.95"} 0.15
```

---

**架构版本**: 1.0  
**最后更新**: 2025-11-08

