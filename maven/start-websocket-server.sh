#!/bin/bash

# WebSocket Debug Proxy Server 启动脚本

set -e

echo "=== WebSocket Debug Proxy Server 启动脚本 ==="
echo ""

# 检查是否已编译
if [ ! -f "debug-proxy-server/target/debug-proxy-server-1.0-SNAPSHOT.jar" ]; then
    echo "⚠️  未找到编译后的 jar 文件，开始编译..."
    cd debug-proxy-server
    mvn clean package -DskipTests
    cd ..
    echo "✅ 编译完成"
    echo ""
fi

# 默认配置
PORT=${1:-18888}
SECRET_KEY=${2:-"default-secret-key-change-me"}
API_KEY=${DEBUG_API_KEY:-"debug-key-12345"}

echo "配置信息:"
echo "  端口: $PORT"
echo "  JWT 密钥: ${SECRET_KEY:0:10}..."
echo "  API Key: $API_KEY"
echo ""

# 设置环境变量
export DEBUG_API_KEY=$API_KEY

echo "🚀 启动 WebSocket Debug Proxy Server..."
echo ""

# 启动服务器
cd debug-proxy-server
java -cp target/debug-proxy-server-1.0-SNAPSHOT.jar \
  com.example.proxy.server.WebSocketDebugProxyServer \
  $PORT \
  "$SECRET_KEY"

