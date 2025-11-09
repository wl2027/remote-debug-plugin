#!/bin/bash
# Script to run the debug proxy server

PORT=${1:-8888}

echo "Starting Debug Proxy Server..."
echo "Listening on port: $PORT"
echo ""

java -jar debug-proxy-server/target/debug-proxy-server-1.0-SNAPSHOT.jar $PORT

