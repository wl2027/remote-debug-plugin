#!/bin/bash
# Script to run the demo application with debug enabled

echo "Starting Demo Application with debug enabled..."
echo "Debug port: 5006"
echo ""

java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006 \
     -jar demo-app/target/demo-app-1.0-SNAPSHOT.jar

