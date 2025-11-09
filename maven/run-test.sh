#!/bin/bash

# 完整测试脚本：JDI <--JDWP--> Client <--WebSocket--> Server <--JDWP--> App
# 测试简化后的 WebSocket 远程调试方案（无认证）

set -e

echo "======================================"
echo "WebSocket 远程调试完整测试"
echo "======================================"
echo ""

# 清理之前的进程
echo "清理之前的测试进程..."
pkill -f "debug-proxy-server" || true
pkill -f "debug-proxy-client" || true
pkill -f "demo-app" || true
pkill -f "jdi-debugger" || true
sleep 2

# 配置
SERVER_PORT=18888
TARGET_APP_PORT=15006
CLIENT_LOCAL_PORT=15005

echo "配置信息:"
echo "  Server WebSocket 端口: $SERVER_PORT"
echo "  Target App JDWP 端口: $TARGET_APP_PORT"
echo "  Client 本地端口: $CLIENT_LOCAL_PORT"
echo "  JDI 连接端口: $CLIENT_LOCAL_PORT"
echo ""

# 启动 Proxy Server
echo "1. 启动 WebSocket Proxy Server..."
cd debug-proxy-server
java -jar target/debug-proxy-server-1.0-SNAPSHOT.jar \
  $SERVER_PORT > /tmp/proxy-server.log 2>&1 &
SERVER_PID=$!
cd ..
sleep 2

if ps -p $SERVER_PID > /dev/null; then
  echo "   ✓ Server 启动成功 (PID: $SERVER_PID)"
else
  echo "   ✗ Server 启动失败"
  cat /tmp/proxy-server.log
  exit 1
fi

# 启动测试应用（目标 JVM，带 JDWP）
echo ""
echo "2. 启动测试应用 (DemoApplication，JDWP 端口 $TARGET_APP_PORT)..."
cd demo-app
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$TARGET_APP_PORT \
  -cp target/demo-app-1.0-SNAPSHOT.jar \
  com.example.demo.DemoApplication > /tmp/demo-app.log 2>&1 &
APP_PID=$!
cd ..
sleep 3

if ps -p $APP_PID > /dev/null; then
  echo "   ✓ Demo App 启动成功 (PID: $APP_PID)"
  echo "   日志: tail -f /tmp/demo-app.log"
else
  echo "   ✗ Demo App 启动失败"
  cat /tmp/demo-app.log
  exit 1
fi

# 启动 Proxy Client
echo ""
echo "3. 启动 WebSocket Proxy Client..."
cd debug-proxy-client
java -jar target/debug-proxy-client-1.0-SNAPSHOT.jar \
  ws://127.0.0.1:$SERVER_PORT \
  127.0.0.1 \
  $TARGET_APP_PORT \
  $CLIENT_LOCAL_PORT \
  test-app > /tmp/proxy-client.log 2>&1 &
CLIENT_PID=$!
cd ..
sleep 2

if ps -p $CLIENT_PID > /dev/null; then
  echo "   ✓ Client 启动成功 (PID: $CLIENT_PID)"
else
  echo "   ✗ Client 启动失败"
  cat /tmp/proxy-client.log
  exit 1
fi

# 启动 JDI Debugger（模拟 IDEA）
echo ""
echo "4. 启动 JDI Debugger (模拟 IDEA 调试)..."
echo "   连接到 localhost:$CLIENT_LOCAL_PORT"
sleep 2

cd jdi-debugger
java -cp target/jdi-debugger-1.0-SNAPSHOT.jar \
  com.example.debugger.SimpleJDIDebugger \
  localhost \
  $CLIENT_LOCAL_PORT

# JDI Debugger 会在命中 3 次断点后自动退出

echo ""
echo "======================================"
echo "测试完成！"
echo "======================================"
echo ""
echo "组件状态:"
echo "  Server (PID: $SERVER_PID): $(ps -p $SERVER_PID > /dev/null && echo '运行中' || echo '已停止')"
echo "  Client (PID: $CLIENT_PID): $(ps -p $CLIENT_PID > /dev/null && echo '运行中' || echo '已停止')"  
echo "  Demo App (PID: $APP_PID): $(ps -p $APP_PID > /dev/null && echo '运行中' || echo '已停止')"
echo ""
echo "日志文件:"
echo "  Server: /tmp/proxy-server.log"
echo "  Client: /tmp/proxy-client.log"
echo "  Demo App: /tmp/demo-app.log"
echo ""

# 清理
echo "清理测试进程..."
kill $SERVER_PID $CLIENT_PID $APP_PID 2>/dev/null || true
sleep 1
pkill -f "debug-proxy-server" || true
pkill -f "debug-proxy-client" || true
pkill -f "demo-app" || true

echo ""
echo "✓ 测试完成并清理完毕"

