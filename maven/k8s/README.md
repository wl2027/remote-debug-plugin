# Kubernetes 部署指南

## 构建 Docker 镜像

### 1. 构建 Debug Proxy Server 镜像

```bash
cd debug-proxy-server
mvn clean package
docker build -t your-registry/debug-proxy-server:1.0 .
docker push your-registry/debug-proxy-server:1.0
```

### 2. 构建 Demo App 镜像

```bash
cd demo-app
mvn clean package

# 创建 Dockerfile
cat > Dockerfile << 'EOF'
FROM openjdk:11-jre-slim
WORKDIR /app
COPY target/demo-app-1.0-SNAPSHOT.jar /app/demo-app.jar
ENTRYPOINT ["java", "-jar", "/app/demo-app.jar"]
EOF

docker build -t your-registry/demo-app:1.0 .
docker push your-registry/demo-app:1.0
```

## 部署到 Kubernetes

### 1. 部署 Debug Proxy Server

```bash
kubectl apply -f k8s/deployment.yaml
```

这会创建:
- Namespace: `debug-system`
- Deployment: `debug-proxy-server`
- Service: NodePort on port 30888

验证部署:
```bash
kubectl get pods -n debug-system
kubectl get svc -n debug-system
```

### 2. 部署 Demo App (可选)

```bash
kubectl apply -f k8s/demo-app-deployment.yaml
```

验证部署:
```bash
kubectl get pods -n default -l app=demo-app
```

## 连接到集群内的 Pod

### 方式 1: 使用 NodePort

```bash
# 获取节点 IP
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}')

# 启动 proxy-client 连接到 NodePort
./run-proxy-client.sh $NODE_IP 30888 5005 demo-app-xxx demo-app.default 5005
```

### 方式 2: 使用端口转发

```bash
# 转发 proxy-server 端口
kubectl port-forward -n debug-system svc/debug-proxy-server 8888:8888 &

# 获取目标 Pod 信息
POD_NAME=$(kubectl get pod -l app=demo-app -o jsonpath='{.items[0].metadata.name}')
POD_IP=$(kubectl get pod $POD_NAME -o jsonpath='{.status.podIP}')

# 启动 proxy-client
./run-proxy-client.sh localhost 8888 5005 $POD_NAME $POD_IP 5005
```

### 方式 3: 使用 LoadBalancer (云环境)

修改 `k8s/deployment.yaml` 中的 Service type:
```yaml
spec:
  type: LoadBalancer  # 改为 LoadBalancer
```

然后:
```bash
kubectl apply -f k8s/deployment.yaml

# 获取 LoadBalancer IP
LB_IP=$(kubectl get svc -n debug-system debug-proxy-server -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

# 启动 proxy-client
./run-proxy-client.sh $LB_IP 8888 5005 demo-app-xxx demo-app.default 5005
```

## 配置 RBAC (可选 - 用于自动 Pod 发现)

如果你想让 proxy-server 自动发现和连接 Pod,需要配置 RBAC:

```bash
kubectl apply -f k8s/rbac.yaml
```

然后在应用代码中使用 Kubernetes API:
```java
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

KubernetesClient client = new DefaultKubernetesClient();
Pod pod = client.pods()
    .inNamespace(namespace)
    .withName(podName)
    .get();
String podIP = pod.getStatus().getPodIP();
```

## 安全考虑

### 1. 网络策略

创建 NetworkPolicy 限制访问:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: debug-proxy-server-netpol
  namespace: debug-system
spec:
  podSelector:
    matchLabels:
      app: debug-proxy-server
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - ipBlock:
        cidr: 10.0.0.0/8  # 只允许内网访问
    ports:
    - protocol: TCP
      port: 8888
  egress:
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 5005  # JDWP 端口
```

### 2. 限制调试端口暴露

在生产环境中,确保调试端口不对外暴露:
```yaml
ports:
- containerPort: 5005
  name: debug
  protocol: TCP
  # 不要创建 LoadBalancer Service
```

### 3. 使用 Secret 管理凭证

如果添加了认证,使用 Secret 存储凭证:
```bash
kubectl create secret generic debug-proxy-creds \
  --from-literal=username=admin \
  --from-literal=password=your-secure-password \
  -n debug-system
```

## 监控和日志

### 查看日志

```bash
# Proxy server 日志
kubectl logs -n debug-system -l app=debug-proxy-server -f

# Demo app 日志
kubectl logs -l app=demo-app -f
```

### 添加 Prometheus 监控

修改 Deployment 添加 annotations:
```yaml
metadata:
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "8888"
    prometheus.io/path: "/metrics"
```

## 故障排查

### 检查 Pod 状态
```bash
kubectl get pods -n debug-system
kubectl describe pod -n debug-system <pod-name>
```

### 检查服务
```bash
kubectl get svc -n debug-system
kubectl describe svc -n debug-system debug-proxy-server
```

### 测试连接
```bash
# 从集群内测试
kubectl run -it --rm debug --image=busybox --restart=Never -- \
  telnet debug-proxy-server.debug-system.svc.cluster.local 8888

# 从集群外测试
telnet <NODE_IP> 30888
```

### 查看事件
```bash
kubectl get events -n debug-system --sort-by='.lastTimestamp'
```

## 清理

```bash
# 删除所有资源
kubectl delete -f k8s/deployment.yaml
kubectl delete -f k8s/demo-app-deployment.yaml

# 或删除整个命名空间
kubectl delete namespace debug-system
```

## 下一步

1. ✅ 部署到开发集群测试
2. 🔒 添加安全措施 (TLS, 认证)
3. 📊 集成监控和告警
4. 📚 编写操作手册
5. 🚀 推广到团队使用

