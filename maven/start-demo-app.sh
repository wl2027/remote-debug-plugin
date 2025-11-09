#!/bin/bash

# 启动 Demo 应用（带 JDWP 调试端口）

set -e

JDWP_PORT=15006

echo "======================================"
echo "启动 Demo 应用"
echo "======================================"
echo ""
echo "JDWP 端口: $JDWP_PORT"
echo ""

# 检查端口是否被占用
if lsof -Pi :$JDWP_PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "⚠️  端口 $JDWP_PORT 已被占用，正在清理..."
    lsof -ti:$JDWP_PORT | xargs kill -9 2>/dev/null || true
    sleep 1
fi

# 检查 jar 文件是否存在
if [ ! -f "demo-app/target/demo-app-1.0-SNAPSHOT.jar" ]; then
    echo "编译 demo-app..."
    cd demo-app && mvn clean package -DskipTests && cd ..
fi

echo "启动 Demo 应用..."
cd demo-app

java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$JDWP_PORT \
     -cp target/demo-app-1.0-SNAPSHOT.jar \
     com.example.demo.DemoApplication

# 脚本会在这里阻塞，应用在前台运行

