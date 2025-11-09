#!/bin/bash

# 检查所有组件的运行状态

echo "======================================"
echo "检查组件运行状态"
echo "======================================"
echo ""

# 检查端口占用
echo "1. 检查端口占用情况:"
echo ""

check_port() {
    local port=$1
    local name=$2
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        local pid=$(lsof -ti:$port)
        local cmd=$(ps -p $pid -o command=)
        echo "  ✅ $name (端口 $port) - 运行中"
        echo "     PID: $pid"
        echo "     CMD: $cmd"
    else
        echo "  ❌ $name (端口 $port) - 未运行"
    fi
    echo ""
}

check_port 18888 "Proxy Server"
check_port 15006 "Demo App (JDWP)"
check_port 15005 "Proxy Client"

# 检查进程
echo "2. 检查相关进程:"
echo ""

check_process() {
    local pattern=$1
    local name=$2
    local pids=$(pgrep -f "$pattern")
    if [ -n "$pids" ]; then
        echo "  ✅ $name - 运行中"
        for pid in $pids; do
            echo "     PID: $pid - $(ps -p $pid -o command=)"
        done
    else
        echo "  ❌ $name - 未运行"
    fi
    echo ""
}

check_process "debug-proxy-server" "Proxy Server"
check_process "demo-app" "Demo App"
check_process "debug-proxy-client" "Proxy Client"

# 检查 demo-app 日志
echo "3. 检查 Demo App 状态日志:"
echo ""
if [ -f "/tmp/demo-app-status.log" ]; then
    echo "  最近 5 条日志:"
    tail -5 /tmp/demo-app-status.log | sed 's/^/  /'
    echo ""
    echo "  完整日志: cat /tmp/demo-app-status.log"
else
    echo "  ⚠️  日志文件不存在: /tmp/demo-app-status.log"
    echo "  (可能 demo-app 还未启动或使用旧版本)"
fi
echo ""

# 检查其他日志
echo "4. 可用的日志文件:"
echo ""
for log in /tmp/demo-app.log /tmp/proxy-server.log /tmp/proxy-client.log; do
    if [ -f "$log" ]; then
        lines=$(wc -l < "$log")
        size=$(ls -lh "$log" | awk '{print $5}')
        echo "  ✅ $log ($lines 行, $size)"
    else
        echo "  ❌ $log (不存在)"
    fi
done

echo ""
echo "======================================"
echo "诊断提示:"
echo "======================================"
echo ""
echo "如果 demo-app 看起来被关闭但调试还能继续："
echo ""
echo "1. 检查 /tmp/demo-app-status.log 中是否有 shutdown hook 信息"
echo "   → 如果有，说明进程真的退出了"
echo "   → 如果没有，说明进程还在运行"
echo ""
echo "2. 使用以下命令实时监控 demo-app:"
echo "   → tail -f /tmp/demo-app.log"
echo "   → tail -f /tmp/demo-app-status.log"
echo ""
echo "3. 检查 demo-app 进程是否真的存在:"
echo "   → ps aux | grep demo-app"
echo "   → lsof -i :15006"
echo ""
echo "4. 如果 IDEA 显示断开但进程还在，可能是："
echo "   → IDEA 的显示问题（实际连接正常）"
echo "   → 网络连接临时中断但已恢复"
echo "   → 需要在 IDEA 中重新连接"
echo ""

