#!/bin/bash

# WebSocket Debug Proxy Client 启动脚本

set -e

echo "=== WebSocket Debug Proxy Client 启动脚本 ==="
echo ""

# 检查参数
if [ $# -lt 3 ]; then
    echo "用法: $0 <server-url> <target-host> <target-port> [local-port] [pod-name] [auth-token]"
    echo ""
    echo "示例:"
    echo "  $0 ws://localhost:18888 localhost 5005"
    echo "  $0 ws://localhost:18888 10.0.1.100 5005 15005 my-pod"
    echo "  $0 wss://proxy.example.com 10.0.1.100 5005 15005 my-pod \"Bearer eyJhbG...\""
    echo ""
    exit 1
fi

# 检查是否已编译
if [ ! -f "debug-proxy-client/target/debug-proxy-client-1.0-SNAPSHOT.jar" ]; then
    echo "⚠️  未找到编译后的 jar 文件，开始编译..."
    cd debug-proxy-client
    mvn clean package -DskipTests
    cd ..
    echo "✅ 编译完成"
    echo ""
fi

# 解析参数
SERVER_URL=$1
TARGET_HOST=$2
TARGET_PORT=$3
LOCAL_PORT=${4:-15005}
POD_NAME=${5:-"unknown-pod"}
AUTH_TOKEN=${6:-"ApiKey debug-key-12345"}

echo "配置信息:"
echo "  Server URL: $SERVER_URL"
echo "  目标主机: $TARGET_HOST"
echo "  目标端口: $TARGET_PORT"
echo "  本地端口: $LOCAL_PORT"
echo "  Pod 名称: $POD_NAME"
echo "  认证 Token: ${AUTH_TOKEN:0:20}..."
echo ""

echo "🚀 启动 WebSocket Debug Proxy Client..."
echo ""
echo "📌 IDEA 调试配置:"
echo "   Host: localhost"
echo "   Port: $LOCAL_PORT"
echo ""

# 启动客户端
cd debug-proxy-client
java -cp target/debug-proxy-client-1.0-SNAPSHOT.jar \
  com.example.proxy.client.WebSocketDebugProxyClient \
  "$SERVER_URL" \
  "$AUTH_TOKEN" \
  "$TARGET_HOST" \
  $TARGET_PORT \
  $LOCAL_PORT \
  "$POD_NAME"

