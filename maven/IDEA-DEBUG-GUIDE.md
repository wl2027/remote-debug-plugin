# IDEA 远程调试指南

## 🎯 问题诊断：demo-app "被关闭"但调试还能继续

### 问题描述

当 IDEA 连接到远程调试后，demo-app 看起来被关闭了，但调试功能还能继续使用。

### 可能的原因

#### 1. IDEA 控制台显示问题（最常见）

**现象**：
- IDEA 底部的控制台显示"进程已结束"
- 但实际上 demo-app 进程还在运行
- 调试功能完全正常

**原因**：
- IDEA 在远程调试模式下，控制台显示的是**本地调试器进程**的状态，而不是远程应用的状态
- 当你使用 "Attach to Remote JVM" 模式时，IDEA 只是连接到远程的 JDWP 端口
- 远程应用的输出**不会**显示在 IDEA 的控制台中

**验证方法**：
```bash
# 1. 检查 demo-app 是否真的在运行
ps aux | grep demo-app

# 2. 检查 JDWP 端口是否在监听
lsof -i :15006

# 3. 查看实时日志
tail -f /tmp/demo-app.log
tail -f /tmp/demo-app-status.log

# 4. 使用诊断脚本
./check-status.sh
```

**解决方案**：
- 这是正常现象，不需要修复
- 要查看远程应用的输出，请：
  - 查看启动应用的终端
  - 查看日志文件：`tail -f /tmp/demo-app.log`
  - 查看状态日志：`tail -f /tmp/demo-app-status.log`

---

#### 2. 应用真的退出了

**现象**：
- 检查进程发现 demo-app 真的不存在了
- 但 JDWP 连接还能用（不太可能）

**原因**：
- 应用代码中有 `System.exit(0)` 或异常导致退出
- JVM 崩溃

**验证方法**：
```bash
# 检查 shutdown hook 是否被触发
cat /tmp/demo-app-status.log | grep "Shutdown Hook"

# 检查是否有错误
cat /tmp/demo-app.log | grep -i error
```

**解决方案**：
- 已在新版 demo-app 中添加了 shutdown hook 检测
- 重新编译并启动 demo-app

---

#### 3. IDEA 显示连接断开但实际正常

**现象**：
- IDEA 状态栏显示"已断开连接"
- 但断点、变量查看等功能还能用

**原因**：
- 网络临时中断后自动恢复
- IDEA 的状态显示延迟

**解决方案**：
- 继续使用，不影响功能
- 或者断开重连

---

## 🚀 正确的使用流程

### 步骤 1: 启动所有组件

```bash
# 方式 A: 一键启动（推荐）
./start-all.sh

# 方式 B: 手动启动
# 终端 1: 启动 demo-app
./start-demo-app.sh

# 终端 2: 启动 proxy-server
cd debug-proxy-server
java -jar target/debug-proxy-server-1.0-SNAPSHOT.jar 18888

# 终端 3: 启动 proxy-client
cd debug-proxy-client
java -jar target/debug-proxy-client-1.0-SNAPSHOT.jar \
  ws://localhost:18888 localhost 15006 15005 test-app
```

### 步骤 2: 配置 IDEA Remote Debug

1. 点击 **Run** → **Edit Configurations**
2. 点击 **+** → **Remote JVM Debug**
3. 配置如下：

```
Name: Remote Debug via Proxy
Debugger mode: Attach to remote JVM
Transport: Socket
Host: localhost
Port: 15005

Use module classpath: (选择你的项目模块)
```

4. 点击 **OK**

### 步骤 3: 开始调试

1. 在代码中设置断点（如 `DemoApplication.processData()` 方法）
2. 点击 Debug 按钮（绿色虫子图标）
3. 选择 "Remote Debug via Proxy"
4. 等待连接成功

**重要提示**：
- ✅ IDEA 控制台可能显示"进程已结束" - **这是正常的！**
- ✅ 调试功能（断点、单步执行、变量查看）完全可用
- ✅ 远程应用的输出在**启动应用的终端**或**日志文件**中查看

### 步骤 4: 监控应用状态

打开新的终端窗口：

```bash
# 实时查看 demo-app 输出
tail -f /tmp/demo-app.log

# 查看状态日志（包含 PID、计数器等）
tail -f /tmp/demo-app-status.log

# 或使用诊断脚本
./check-status.sh
```

---

## 🔍 常见问题排查

### Q1: IDEA 无法连接

**检查**：
```bash
./check-status.sh
```

确保：
- ✅ Demo App 在 15006 端口监听
- ✅ Proxy Server 在 18888 端口运行
- ✅ Proxy Client 在 15005 端口监听

**解决**：
```bash
# 重启所有组件
./start-all.sh
```

### Q2: 断点不生效

**可能原因**：
1. 源代码和运行的 class 文件不匹配
2. 断点设置在了注释或空行上
3. 代码还没执行到断点位置

**解决**：
1. 确保 demo-app 已重新编译：`cd demo-app && mvn clean package`
2. 在有实际代码的行设置断点
3. 等待应用执行到断点（demo-app 每 3 秒循环一次）

### Q3: 变量显示不正确

**可能原因**：
- 代码优化导致变量被优化掉

**解决**：
- 使用 `-Xdebug -Xnoagent` 参数禁用优化（已在 `-agentlib:jdwp` 中自动启用）

---

## 📊 验证调试功能

### 测试清单

在 `DemoApplication.processData()` 方法设置断点，验证：

- [ ] **断点命中** - 程序在断点处暂停
- [ ] **变量查看** - 可以看到 `counter`、`message`、`timestamp` 的值
- [ ] **单步执行** (F8) - 可以逐行执行
- [ ] **步入方法** (F7) - 可以进入 `calculate()` 方法
- [ ] **继续执行** (F9) - 程序继续运行到下一个断点
- [ ] **表达式求值** - 可以在 Evaluate Expression 窗口计算表达式
- [ ] **修改变量** - 可以修改 `counter` 的值（Set Value）

---

## 🎓 理解远程调试的工作原理

```
┌─────────────┐                    ┌─────────────┐
│    IDEA     │                    │  demo-app   │
│  Debugger   │                    │   进程      │
│             │                    │             │
│ • 设置断点   │                    │ • while循环  │
│ • 查看变量   │                    │ • 处理数据   │
│ • 单步执行   │                    │ • 输出日志   │
└──────┬──────┘                    └──────▲──────┘
       │                                  │
       │ JDWP 命令                         │ JDWP 响应
       │                                  │
       ▼                                  │
┌─────────────────────────────────────────┴───┐
│            Proxy Client + Server            │
│         (WebSocket 透明转发)                 │
└─────────────────────────────────────────────┘
```

**关键点**：
1. **IDEA 只是客户端** - 发送调试命令
2. **demo-app 是服务端** - 执行命令并返回结果
3. **控制台输出分离** - IDEA 看不到 demo-app 的 stdout/stderr
4. **IDEA 显示的"进程"** - 是本地调试器连接，不是远程应用

---

## 💡 最佳实践

### 1. 使用多个终端窗口

```bash
# 终端 1: 启动所有组件
./start-all.sh

# 终端 2: 监控 demo-app
tail -f /tmp/demo-app.log

# 终端 3: 监控状态
watch -n 1 './check-status.sh'
```

### 2. 确保代码同步

```bash
# 修改代码后，重新编译 demo-app
cd demo-app
mvn clean package

# 重启 demo-app（使用 Ctrl+C 停止，然后重新运行）
./start-demo-app.sh
```

### 3. 日志文件位置

```bash
/tmp/demo-app.log          # 应用标准输出
/tmp/demo-app-status.log   # 应用状态日志（包含 PID、计数器）
/tmp/proxy-server.log      # Proxy Server 日志
/tmp/proxy-client.log      # Proxy Client 日志
```

---

## 🎉 总结

**"demo-app 被关闭"但调试还能继续 - 这是正常现象！**

原因：
- IDEA 的控制台显示的是本地调试器连接的状态，不是远程应用
- 远程应用实际上一直在运行
- 调试功能完全正常

验证方法：
```bash
./check-status.sh
tail -f /tmp/demo-app.log
```

如果真的有问题，新版 demo-app 会在 `/tmp/demo-app-status.log` 中记录详细信息。

---

**需要帮助？运行 `./check-status.sh` 查看详细诊断信息！**

