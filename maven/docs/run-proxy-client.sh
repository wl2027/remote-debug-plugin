#!/bin/bash
# Script to run the debug proxy client

SERVER_HOST=${1:-localhost}
SERVER_PORT=${2:-8888}
LOCAL_PORT=${3:-5005}
POD_NAME=${4:-my-demo-pod}
TARGET_HOST=${5:-localhost}
TARGET_PORT=${6:-5006}

echo "Starting Debug Proxy Client..."
echo "Configuration:"
echo "  Local port: $LOCAL_PORT (debugger connects here)"
echo "  Server: $SERVER_HOST:$SERVER_PORT"
echo "  Target: $TARGET_HOST:$TARGET_PORT (pod: $POD_NAME)"
echo ""

java -jar debug-proxy-client/target/debug-proxy-client-1.0-SNAPSHOT.jar \
     $SERVER_HOST $SERVER_PORT $LOCAL_PORT $POD_NAME $TARGET_HOST $TARGET_PORT

