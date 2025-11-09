#!/bin/bash

# 启动所有组件进行测试

set -e

echo "======================================"
echo "启动完整测试环境"
echo "======================================"
echo ""

# 配置
SERVER_PORT=18888
TARGET_APP_PORT=15006
CLIENT_LOCAL_PORT=15005

# 清理之前的进程
echo "1. 清理旧进程..."
pkill -f "debug-proxy-server" 2>/dev/null || true
pkill -f "debug-proxy-client" 2>/dev/null || true
pkill -f "demo-app" 2>/dev/null || true
sleep 2

# 检查编译
echo ""
echo "2. 检查编译状态..."
for dir in debug-proxy-server debug-proxy-client demo-app; do
    if [ ! -f "$dir/target/"*".jar" ]; then
        echo "   编译 $dir..."
        cd $dir && mvn clean package -DskipTests && cd ..
    else
        echo "   ✓ $dir 已编译"
    fi
done

echo ""
echo "3. 启动组件..."
echo ""

# 启动 Demo App
echo "   [1/3] 启动 Demo App (JDWP 端口: $TARGET_APP_PORT)..."
cd demo-app
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$TARGET_APP_PORT \
     -cp target/demo-app-1.0-SNAPSHOT.jar \
     com.example.demo.DemoApplication > /tmp/demo-app.log 2>&1 &
APP_PID=$!
cd ..
sleep 2

if ! ps -p $APP_PID > /dev/null; then
    echo "   ✗ Demo App 启动失败"
    cat /tmp/demo-app.log
    exit 1
fi
echo "   ✓ Demo App 启动成功 (PID: $APP_PID)"

# 启动 Proxy Server
echo "   [2/3] 启动 Proxy Server (端口: $SERVER_PORT)..."
cd debug-proxy-server
java -jar target/debug-proxy-server-1.0-SNAPSHOT.jar $SERVER_PORT > /tmp/proxy-server.log 2>&1 &
SERVER_PID=$!
cd ..
sleep 2

if ! ps -p $SERVER_PID > /dev/null; then
    echo "   ✗ Proxy Server 启动失败"
    cat /tmp/proxy-server.log
    kill $APP_PID 2>/dev/null || true
    exit 1
fi
echo "   ✓ Proxy Server 启动成功 (PID: $SERVER_PID)"

# 启动 Proxy Client
echo "   [3/3] 启动 Proxy Client (本地端口: $CLIENT_LOCAL_PORT)..."
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

if ! ps -p $CLIENT_PID > /dev/null; then
    echo "   ✗ Proxy Client 启动失败"
    cat /tmp/proxy-client.log
    kill $SERVER_PID $APP_PID 2>/dev/null || true
    exit 1
fi
echo "   ✓ Proxy Client 启动成功 (PID: $CLIENT_PID)"

echo ""
echo "======================================"
echo "✅ 所有组件启动成功！"
echo "======================================"
echo ""
echo "组件信息:"
echo "  Demo App    (PID: $APP_PID)  - JDWP 端口: $TARGET_APP_PORT"
echo "  Proxy Server (PID: $SERVER_PID) - WS 端口: $SERVER_PORT"
echo "  Proxy Client (PID: $CLIENT_PID) - 本地端口: $CLIENT_LOCAL_PORT"
echo ""
echo "IDEA 调试配置:"
echo "  Host: localhost"
echo "  Port: $CLIENT_LOCAL_PORT"
echo ""
echo "日志文件:"
echo "  Demo App:     tail -f /tmp/demo-app.log"
echo "  Proxy Server: tail -f /tmp/proxy-server.log"
echo "  Proxy Client: tail -f /tmp/proxy-client.log"
echo ""
echo "停止所有组件:"
echo "  kill $SERVER_PID $CLIENT_PID $APP_PID"
echo ""
echo "按 Ctrl+C 退出..."

# 等待信号
trap "echo ''; echo '正在停止所有组件...'; kill $SERVER_PID $CLIENT_PID $APP_PID 2>/dev/null || true; exit 0" INT TERM

# 监控进程
while true; do
    if ! ps -p $APP_PID > /dev/null 2>&1; then
        echo "⚠️  Demo App 已停止"
        break
    fi
    if ! ps -p $SERVER_PID > /dev/null 2>&1; then
        echo "⚠️  Proxy Server 已停止"
        break
    fi
    if ! ps -p $CLIENT_PID > /dev/null 2>&1; then
        echo "⚠️  Proxy Client 已停止"
        break
    fi
    sleep 2
done

# 清理
echo "清理所有进程..."
kill $SERVER_PID $CLIENT_PID $APP_PID 2>/dev/null || true

