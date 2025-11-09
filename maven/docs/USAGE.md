# 使用指南 (Usage Guide)

## 验证结果 (Verification Results)

根据测试日志,系统各组件工作正常:

### ✅ Demo Application
- 成功启动在端口 5006
- JDWP 调试端口正常监听
- 应用正常运行并处理数据

### ✅ Debug Proxy Server  
- 成功启动在端口 8888
- 接收到来自 proxy-client 的连接
- 正确解析路由参数:
  - podName = my-demo-pod
  - targetHost = localhost
  - targetPort = 5006
- 成功连接到目标应用

### ✅ Debug Proxy Client
- 成功启动在端口 5005
- 接收到来自调试器的连接
- 成功发送路由参数到 proxy-server
- 收到服务器确认响应

### ✅ JDI Debugger
- 成功连接到 proxy-client (localhost:5005)
- 通过代理链连接到目标 JVM
- 能够设置断点和进行调试

## 连接流程验证

```
JDI Debugger (localhost:5005)
    ↓ JDWP Protocol
Debug Proxy Client (localhost:5005)
    ↓ Custom Protocol + Routing (podName, targetHost, targetPort)
Debug Proxy Server (localhost:8888)
    ↓ JDWP Protocol
Demo Application (localhost:5006)
```

## 快速启动步骤

### 方式一: 自动测试 (推荐)

```bash
./test-all.sh
```

这个脚本会:
1. 构建所有组件
2. 启动所有服务
3. 运行 JDI 调试器
4. 在 3 次断点后自动清理

### 方式二: 手动启动 (分别在 4 个终端)

#### 终端 1: 启动 Demo 应用
```bash
./run-demo.sh
```

#### 终端 2: 启动 Proxy Server
```bash
./run-proxy-server.sh 8888
```

#### 终端 3: 启动 Proxy Client
```bash
./run-proxy-client.sh localhost 8888 5005 my-demo-pod localhost 5006
```

#### 终端 4: 启动 JDI 调试器
```bash
./run-debugger.sh localhost 5005
```

### 方式三: 使用 IDEA 调试

1. 按照终端 1-3 启动前三个组件

2. 在 IDEA 中配置 Remote JVM Debug:
   - **Run** → **Edit Configurations...**
   - 点击 **+** → **Remote JVM Debug**
   - 配置:
     - Name: `Remote Debug via Proxy`
     - Debugger mode: `Attach to remote JVM`
     - Host: `localhost`
     - Port: `5005`
     - Use module classpath: 选择 `demo-app`

3. 在 `DemoApplication.java` 中设置断点:
   - 建议在 `processData()` 方法的第一行

4. 点击 Debug 按钮开始调试

## 关键参数说明

### debug-proxy-client 参数

```bash
java -jar debug-proxy-client.jar <server-host> <server-port> <local-port> <podName> <targetHost> <targetPort>
```

- `server-host`: proxy-server 的地址
- `server-port`: proxy-server 的端口
- `local-port`: 本地监听端口 (调试器连接这里)
- `podName`: Pod 名称 (用于路由识别)
- `targetHost`: 目标应用的主机地址
- `targetPort`: 目标应用的调试端口

### debug-proxy-server 参数

```bash
java -jar debug-proxy-server.jar <port>
```

- `port`: 服务器监听端口

## 自定义路由参数

系统支持灵活的路由参数配置。在实际 Kubernetes 环境中,可以扩展支持:

### 示例 1: 按 Namespace 和 Pod 路由

```bash
java -jar debug-proxy-client.jar \
    proxy-server.k8s.cluster 8888 5005 \
    my-app-xyz123 \
    my-app-service.production.svc.cluster.local \
    5005
```

### 示例 2: 编程方式添加自定义参数

```java
DebugProxyClient client = new DebugProxyClient(5005, "proxy-server", 8888);

// 添加路由参数
client.addRoutingParam("podName", "my-app-xyz123");
client.addRoutingParam("namespace", "production");
client.addRoutingParam("cluster", "us-west-1");
client.addRoutingParam("targetHost", "10.0.1.50");
client.addRoutingParam("targetPort", "5005");

client.start();
```

## 协议详解

### Custom Routing Protocol

在 JDWP 连接建立之前,proxy-client 会发送路由信息:

```
1. Header: "X-DEBUG-ROUTE" (UTF String)
2. Param Count: N (int)
3. For each param:
   - Key: (UTF String)
   - Value: (UTF String)
4. Server Response: "OK" (UTF String)
5. [开始透明转发 JDWP 协议]
```

### 数据流

```
Client → Server: "X-DEBUG-ROUTE"
Client → Server: 3 (参数数量)
Client → Server: "podName" → "my-demo-pod"
Client → Server: "targetHost" → "localhost"
Client → Server: "targetPort" → "5006"
Server → Client: "OK"
[之后双向透明转发 JDWP 流量]
```

## 故障排查

### 问题: 连接被拒绝

**检查:**
```bash
# 检查端口占用
lsof -i :5005
lsof -i :5006
lsof -i :8888

# 查看进程
ps aux | grep java
```

**解决:**
- 确保按顺序启动: demo-app → proxy-server → proxy-client → debugger
- 检查防火墙设置
- 确认端口没有被其他程序占用

### 问题: 断点不命中

**检查:**
```bash
# 查看 demo-app 日志
tail -f /tmp/demo-app.log

# 确认调试模式
jps -v | grep jdwp
```

**解决:**
- 确认应用以调试模式启动 (`-agentlib:jdwp=...`)
- 确认断点设置在被执行的代码上
- 检查源代码与编译版本是否匹配

### 问题: 路由失败

**检查:**
```bash
# 查看 proxy-server 日志
tail -f /tmp/proxy-server.log

# 查看 proxy-client 日志
tail -f /tmp/proxy-client.log
```

**解决:**
- 确认路由参数正确
- 检查目标主机和端口可达
- 查看服务器日志中的错误信息

## 在 Kubernetes 中部署

### 1. 部署 Debug Proxy Server

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: debug-proxy-server
  namespace: debug-system
spec:
  replicas: 1
  selector:
    matchLabels:
      app: debug-proxy-server
  template:
    metadata:
      labels:
        app: debug-proxy-server
    spec:
      serviceAccountName: debug-proxy-sa
      containers:
      - name: server
        image: your-registry/debug-proxy-server:1.0
        ports:
        - containerPort: 8888
          name: proxy
        args: ["8888"]
---
apiVersion: v1
kind: Service
metadata:
  name: debug-proxy-server
  namespace: debug-system
spec:
  type: NodePort
  selector:
    app: debug-proxy-server
  ports:
  - port: 8888
    targetPort: 8888
    nodePort: 30888
```

### 2. 配置 RBAC (如需访问 Pod)

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: debug-proxy-sa
  namespace: debug-system
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: debug-proxy-role
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list"]
- apiGroups: [""]
  resources: ["pods/portforward"]
  verbs: ["create"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: debug-proxy-binding
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: debug-proxy-role
subjects:
- kind: ServiceAccount
  name: debug-proxy-sa
  namespace: debug-system
```

### 3. 本地运行 Proxy Client

```bash
# 获取 proxy-server 地址
PROXY_SERVER=$(kubectl get svc -n debug-system debug-proxy-server -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

# 或使用 NodePort
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}')
PROXY_SERVER=$NODE_IP:30888

# 启动 proxy-client
./run-proxy-client.sh $PROXY_SERVER 8888 5005 my-app-pod-xyz my-app-service.production 5005
```

## 性能优化

### 调整缓冲区大小

修改 `DebugProxyServer.java` 和 `DebugProxyClient.java`:

```java
byte[] buffer = new byte[16384];  // 增加缓冲区
```

### 启用连接池

对于频繁连接,可以实现连接复用。

### 监控和日志

添加详细日志记录每次连接:
- 连接时间
- 数据传输量
- 延迟统计

## 扩展功能

### 1. 多 Pod 负载均衡

在 proxy-server 中实现 Pod 选择策略:
- 轮询 (Round Robin)
- 最少连接 (Least Connections)
- 哈希 (Hash-based)

### 2. 安全增强

- 添加 TLS/SSL 加密
- 实现 Token 认证
- 集成 OAuth2

### 3. Web UI

开发管理界面:
- 查看活跃连接
- 管理路由规则
- 实时监控

## 贡献

欢迎提交 Issue 和 Pull Request!

## 许可证

MIT License

