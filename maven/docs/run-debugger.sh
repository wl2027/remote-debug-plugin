#!/bin/bash
# Script to run the JDI debugger

HOST=${1:-localhost}
PORT=${2:-5005}

echo "Starting JDI Debugger..."
echo "Connecting to: $HOST:$PORT"
echo ""

java -jar jdi-debugger/target/jdi-debugger-1.0-SNAPSHOT.jar $HOST $PORT

