#!/bin/bash
# Complete test script - runs all components in sequence

set -e

echo "=========================================="
echo "Remote Debug Proxy System - Full Test"
echo "=========================================="
echo ""

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Step 1: Build
echo -e "${BLUE}Step 1: Building all components...${NC}"
mvn clean package -q
echo -e "${GREEN}✓ Build completed${NC}"
echo ""

# Step 2: Start demo app
echo -e "${BLUE}Step 2: Starting Demo Application (port 5006)...${NC}"
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006 \
     -jar demo-app/target/demo-app-1.0-SNAPSHOT.jar > /tmp/demo-app.log 2>&1 &
DEMO_PID=$!
sleep 2
echo -e "${GREEN}✓ Demo app started (PID: $DEMO_PID)${NC}"
echo ""

# Step 3: Start proxy server
echo -e "${BLUE}Step 3: Starting Debug Proxy Server (port 8888)...${NC}"
java -jar debug-proxy-server/target/debug-proxy-server-1.0-SNAPSHOT.jar 8888 > /tmp/proxy-server.log 2>&1 &
SERVER_PID=$!
sleep 2
echo -e "${GREEN}✓ Proxy server started (PID: $SERVER_PID)${NC}"
echo ""

# Step 4: Start proxy client
echo -e "${BLUE}Step 4: Starting Debug Proxy Client (port 5005)...${NC}"
java -jar debug-proxy-client/target/debug-proxy-client-1.0-SNAPSHOT.jar \
     localhost 8888 5005 my-demo-pod localhost 5006 > /tmp/proxy-client.log 2>&1 &
CLIENT_PID=$!
sleep 2
echo -e "${GREEN}✓ Proxy client started (PID: $CLIENT_PID)${NC}"
echo ""

# Step 5: Run debugger
echo -e "${BLUE}Step 5: Running JDI Debugger...${NC}"
echo ""
java -jar jdi-debugger/target/jdi-debugger-1.0-SNAPSHOT.jar localhost 5005

# Cleanup
echo ""
echo -e "${BLUE}Cleaning up...${NC}"
kill $DEMO_PID $SERVER_PID $CLIENT_PID 2>/dev/null || true
echo -e "${GREEN}✓ All processes stopped${NC}"
echo ""

echo "=========================================="
echo "Test completed successfully!"
echo "=========================================="
echo ""
echo "Log files:"
echo "  - Demo app: /tmp/demo-app.log"
echo "  - Proxy server: /tmp/proxy-server.log"
echo "  - Proxy client: /tmp/proxy-client.log"

