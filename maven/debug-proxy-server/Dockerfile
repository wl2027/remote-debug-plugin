FROM openjdk:11-jre-slim

LABEL maintainer="proxy-debug"
LABEL description="Debug Proxy Server for Kubernetes remote debugging"

WORKDIR /app

# Copy the jar file
COPY target/debug-proxy-server-1.0-SNAPSHOT.jar /app/debug-proxy-server.jar

# Expose the proxy port
EXPOSE 8888

# Run the server
ENTRYPOINT ["java", "-jar", "/app/debug-proxy-server.jar"]
CMD ["8888"]

