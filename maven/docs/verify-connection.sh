#!/bin/bash
# Quick verification script - just checks if connections work

set -e

echo "=========================================="
echo "Quick Connection Verification"
echo "=========================================="
echo ""

# Build
echo "Building..."
mvn clean package -q
echo "✓ Build completed"
echo ""

# Start demo app
echo "Starting demo app (port 5006)..."
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006 \
     -jar demo-app/target/demo-app-1.0-SNAPSHOT.jar > /tmp/demo-app.log 2>&1 &
DEMO_PID=$!
sleep 2

# Start proxy server
echo "Starting proxy server (port 8888)..."
java -jar debug-proxy-server/target/debug-proxy-server-1.0-SNAPSHOT.jar 8888 > /tmp/proxy-server.log 2>&1 &
SERVER_PID=$!
sleep 2

# Start proxy client
echo "Starting proxy client (port 5005)..."
java -jar debug-proxy-client/target/debug-proxy-client-1.0-SNAPSHOT.jar \
     localhost 8888 5005 my-demo-pod localhost 5006 > /tmp/proxy-client.log 2>&1 &
CLIENT_PID=$!
sleep 2

echo ""
echo "All components started!"
echo "  - Demo app PID: $DEMO_PID (debug port 5006)"
echo "  - Proxy server PID: $SERVER_PID (port 8888)"
echo "  - Proxy client PID: $CLIENT_PID (port 5005)"
echo ""

# Test connection
echo "Testing JDI connection..."
java -jar jdi-debugger/target/jdi-debugger-1.0-SNAPSHOT.jar localhost 5005 > /tmp/debugger-test.log 2>&1 &
DEBUGGER_PID=$!
sleep 5

# Check if debugger connected
if grep -q "Successfully connected to target JVM" /tmp/debugger-test.log; then
    echo "✓ JDI debugger connected successfully!"
    echo ""
    echo "Connection chain verified:"
    echo "  JDI Debugger → Proxy Client (5005) → Proxy Server (8888) → Demo App (5006)"
    echo ""
    
    # Show routing info
    echo "Routing parameters from logs:"
    grep "Routing param:" /tmp/proxy-server.log | head -5
    echo ""
    
    SUCCESS=true
else
    echo "✗ Connection test failed"
    echo "Check logs for details:"
    echo "  - /tmp/demo-app.log"
    echo "  - /tmp/proxy-server.log"
    echo "  - /tmp/proxy-client.log"
    echo "  - /tmp/debugger-test.log"
    SUCCESS=false
fi

# Cleanup
echo ""
echo "Cleaning up..."
kill $DEMO_PID $SERVER_PID $CLIENT_PID $DEBUGGER_PID 2>/dev/null || true
sleep 1

if [ "$SUCCESS" = true ]; then
    echo ""
    echo "=========================================="
    echo "✓ Verification PASSED"
    echo "=========================================="
    echo ""
    echo "System is ready to use!"
    echo ""
    echo "Next steps:"
    echo "  1. Manual test: ./test-all.sh"
    echo "  2. Use with IDEA: See USAGE.md"
    echo ""
    exit 0
else
    echo ""
    echo "=========================================="
    echo "✗ Verification FAILED"
    echo "=========================================="
    exit 1
fi

