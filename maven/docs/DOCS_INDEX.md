# 📚 文档索引

## 快速导航

### 🚀 新手入门
1. [**QUICK_START.md**](QUICK_START.md) - 5分钟快速上手
2. [**README.md**](../README.md) - 项目概览和说明

### 📖 使用指南
3. [**USAGE.md**](USAGE.md) - 详细使用指南
4. [**k8s/README.md**](../k8s/README.md) - Kubernetes 部署指南

### 🏗️ 技术文档
5. [**ARCHITECTURE.md**](ARCHITECTURE.md) - 系统架构详解
6. [**PROJECT_SUMMARY.md**](PROJECT_SUMMARY.md) - 项目技术总结

### ✅ 项目管理
7. [**COMPLETION_REPORT.md**](COMPLETION_REPORT.md) - 项目完成报告
8. [**REMADE.md**](../需求.md) - 原始需求文档

---

## 文档矩阵

| 文档 | 目标读者 | 阅读时间 | 优先级 |
|-----|---------|---------|-------|
| [QUICK_START.md](QUICK_START.md) | 所有用户 | 5 分钟 | ⭐⭐⭐ |
| [README.md](../README.md) | 所有用户 | 10 分钟 | ⭐⭐⭐ |
| [USAGE.md](USAGE.md) | 使用者 | 20 分钟 | ⭐⭐ |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 开发者 | 30 分钟 | ⭐⭐ |
| [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) | 开发者 | 15 分钟 | ⭐ |
| [k8s/README.md](../k8s/README.md) | 运维人员 | 15 分钟 | ⭐⭐ |
| [COMPLETION_REPORT.md](COMPLETION_REPORT.md) | 项目经理 | 10 分钟 | ⭐ |

---

## 按场景查找

### 场景 1: 我想快速测试系统
➡️ [QUICK_START.md](QUICK_START.md) → 一键测试命令

### 场景 2: 我想了解系统是如何工作的
➡️ [ARCHITECTURE.md](ARCHITECTURE.md) → 架构图和数据流

### 场景 3: 我想在本地手动测试
➡️ [USAGE.md](USAGE.md) → 手动启动步骤

### 场景 4: 我想部署到 Kubernetes
➡️ [k8s/README.md](../k8s/README.md) → 部署指南

### 场景 5: 我想用 IDEA 进行调试
➡️ [USAGE.md](USAGE.md) → "使用 IDEA 进行调试" 章节

### 场景 6: 我遇到了问题
➡️ [USAGE.md](USAGE.md) → "故障排查" 章节

### 场景 7: 我想了解技术细节
➡️ [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) → 技术实现

### 场景 8: 我需要验收项目
➡️ [COMPLETION_REPORT.md](COMPLETION_REPORT.md) → 验收标准

---

## 按角色查找

### 👨‍💻 开发者
1. [QUICK_START.md](QUICK_START.md) - 快速上手
2. [ARCHITECTURE.md](ARCHITECTURE.md) - 理解架构
3. [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) - 技术细节
4. [USAGE.md](USAGE.md) - 调试技巧

### 🔧 运维人员
1. [README.md](../README.md) - 了解项目
2. [k8s/README.md](../k8s/README.md) - K8s 部署
3. [USAGE.md](USAGE.md) - 故障排查
4. [ARCHITECTURE.md](ARCHITECTURE.md) - 网络架构

### 📊 项目经理
1. [COMPLETION_REPORT.md](COMPLETION_REPORT.md) - 项目状态
2. [README.md](../README.md) - 功能概览
3. [QUICK_START.md](QUICK_START.md) - Demo 演示

### 🎓 新用户
1. [QUICK_START.md](QUICK_START.md) - 从这里开始
2. [README.md](../README.md) - 了解更多
3. [USAGE.md](USAGE.md) - 深入使用

---

## 文档内容概览

### 📄 QUICK_START.md
```
• 一键测试命令
• 手动测试步骤 (4 个终端)
• 端口映射说明
• 连接流程图
• 验证成功标志
• 故障排查
• 命令速查
```

### 📄 README.md
```
• 系统架构图
• 组件说明
• 快速开始
• 运行完整测试流程
• 使用 IDEA 调试
• 自定义参数
• Kubernetes 部署
• 故障排查
• 扩展功能
```

### 📄 USAGE.md
```
• 验证结果
• 连接流程验证
• 快速启动步骤
• 关键参数说明
• 自定义路由参数
• 协议详解
• 故障排查
• Kubernetes 部署
• 性能优化
• 扩展功能
```

### 📄 ARCHITECTURE.md
```
• 总体架构图
• 数据流详解
• 协议详解
• 核心类设计
• 部署架构
• 网络流量分析
• 安全架构
• 性能优化
• 监控指标
```

### 📄 PROJECT_SUMMARY.md
```
• 项目概述
• 系统架构
• 已实现的组件
• 自定义协议设计
• 测试验证
• 辅助工具
• 技术要点
• 实际部署指南
• 扩展功能建议
• 项目文件清单
```

### 📄 k8s/README.md
```
• 构建 Docker 镜像
• 部署到 Kubernetes
• 连接到集群内的 Pod
• 配置 RBAC
• 安全考虑
• 监控和日志
• 故障排查
• 清理
```

### 📄 COMPLETION_REPORT.md
```
• 项目状态
• 需求完成情况
• 交付成果
• 项目结构
• 测试验证
• 核心特性
• 技术指标
• 使用场景
• 安全建议
• 扩展功能建议
• 技术亮点
• 验收标准
```

---

## 命令速查

### 构建和测试
```bash
# 构建项目
mvn clean package

# 快速验证 (推荐)
./verify-connection.sh

# 完整测试
./test-all.sh
```

### 手动启动
```bash
# 终端 1: Demo App
./run-demo.sh

# 终端 2: Proxy Server
./run-proxy-server.sh

# 终端 3: Proxy Client
./run-proxy-client.sh localhost 8888 5005 pod-name host 5006

# 终端 4: Debugger
./run-debugger.sh localhost 5005
```

### Kubernetes
```bash
# 部署
kubectl apply -f k8s/deployment.yaml

# 查看状态
kubectl get pods -n debug-system

# 查看日志
kubectl logs -n debug-system -l app=debug-proxy-server -f
```

---

## 关键概念

### 端口
- **5005**: proxy-client 监听端口 (IDEA 连接这里)
- **5006**: demo-app 调试端口
- **8888**: proxy-server 监听端口
- **30888**: Kubernetes NodePort

### 组件
- **demo-app**: 被调试的目标应用
- **debug-proxy-server**: 集群内代理服务器
- **debug-proxy-client**: 本地代理客户端
- **jdi-debugger**: JDI 调试器 (模拟 IDEA)

### 协议
- **JDWP**: Java Debug Wire Protocol
- **Custom Protocol**: 自定义路由协议

### 路由参数
- **podName**: Pod 名称
- **targetHost**: 目标主机
- **targetPort**: 目标端口

---

## 外部资源

### 官方文档
- [Java Debug Wire Protocol](https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/jdwp-spec.html)
- [Java Debug Interface](https://docs.oracle.com/javase/8/docs/jdk/api/jpda/jdi/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)

### 相关工具
- [IntelliJ IDEA Remote Debug](https://www.jetbrains.com/help/idea/tutorial-remote-debug.html)
- [kubectl port-forward](https://kubernetes.io/docs/reference/generated/kubectl/kubectl-commands#port-forward)

---

## 版本历史

| 版本 | 日期 | 更新内容 |
|-----|------|---------|
| 1.0 | 2025-11-08 | 初始版本,所有功能完成 |

---

## 反馈和贡献

如有问题或建议,请:
1. 查看相关文档
2. 查看故障排查章节
3. 提交 Issue
4. 贡献 Pull Request

---

**Happy Debugging! 🐛✨**

