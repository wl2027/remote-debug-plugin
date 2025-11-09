package com.example.proxy.server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简化版 WebSocket Debug Proxy Server
 * 移除认证机制，专注于 JDWP 协议转发
 * 
 * 架构: JDI <--JDWP--> proxy-client <--WebSocket--> proxy-server <--JDWP--> JVM
 */
public class WebSocketDebugProxyServer extends WebSocketServer {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketDebugProxyServer.class);
    private static final int DEFAULT_PORT = 18888;
    
    // 会话管理：WebSocket 连接 -> JVM Socket 映射
    private final Map<WebSocket, DebugSession> sessions = new ConcurrentHashMap<>();
    
    public WebSocketDebugProxyServer(int port) {
        super(new InetSocketAddress(port));
        logger.info("WebSocket Debug Proxy Server initialized on port {}", port);
    }
    
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        
        WebSocketDebugProxyServer server = new WebSocketDebugProxyServer(port);
        server.start();
        
        logger.info("WebSocket Debug Proxy Server started on port {}", port);
        logger.info("Waiting for connections...");
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        try {
            logger.info("New WebSocket connection from: {}", conn.getRemoteSocketAddress());
            
            // 解析目标 JVM 信息
            String targetHost = handshake.getFieldValue("X-Target-Host");
            String targetPortStr = handshake.getFieldValue("X-Target-Port");
            String podName = handshake.getFieldValue("X-Pod-Name");
            String sessionId = handshake.getFieldValue("X-Session-Id");
            
            if (targetHost == null || targetPortStr == null) {
                logger.error("Missing target host or port in handshake");
                conn.close(1008, "Missing target information");
                return;
            }
            
            int targetPort = Integer.parseInt(targetPortStr);
            logger.info("Session {}: Connecting to pod '{}' at {}:{}", 
                       sessionId, podName, targetHost, targetPort);
            
            // 连接到目标 JVM
            Socket jvmSocket = new Socket(targetHost, targetPort);
            logger.info("Session {}: Connected to target JVM", sessionId);
            
            // 创建会话
            DebugSession session = new DebugSession(sessionId, podName, conn, jvmSocket);
            sessions.put(conn, session);
            
            // 启动 JVM -> WebSocket 转发线程
            session.startForwarding();
            
            logger.info("Session {}: Debug session established successfully", sessionId);
            
        } catch (Exception e) {
            logger.error("Error handling WebSocket connection: {}", e.getMessage(), e);
            try {
                conn.close(1011, "Cannot connect to target: " + e.getMessage());
            } catch (Exception closeEx) {
                logger.debug("Error closing connection: {}", closeEx.getMessage());
            }
        }
    }
    
    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        // 接收来自 client 的 JDWP 数据，转发到 JVM
        DebugSession session = sessions.get(conn);
        if (session == null) {
            logger.warn("Received message for unknown session");
            conn.close(1008, "Unknown session");
            return;
        }
        
        try {
            byte[] data = new byte[message.remaining()];
            message.get(data);
            session.forwardToJvm(data);
        } catch (IOException e) {
            logger.error("Session {}: Error forwarding data to JVM: {}", 
                        session.getSessionId(), e.getMessage(), e);
            closeSession(conn);
        }
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        // 文本消息用于控制命令（如心跳）
        logger.debug("Received text message: {}", message);
        
        if ("PING".equals(message)) {
            conn.send("PONG");
        }
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.info("WebSocket connection closed: code={}, reason={}, remote={}", 
                   code, reason, remote);
        closeSession(conn);
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error("WebSocket error: {}", ex.getMessage(), ex);
        if (conn != null) {
            closeSession(conn);
        }
    }
    
    @Override
    public void onStart() {
        logger.info("WebSocket server started successfully");
        setConnectionLostTimeout(30);
    }
    
    private void closeSession(WebSocket conn) {
        DebugSession session = sessions.remove(conn);
        if (session != null) {
            session.close();
            logger.info("Session {}: Closed", session.getSessionId());
        }
    }
    
    /**
     * 调试会话，管理 WebSocket 和 JVM Socket 之间的双向转发
     */
    private static class DebugSession {
        private final String sessionId;
        private final String podName;
        private final WebSocket webSocket;
        private final Socket jvmSocket;
        private volatile boolean running = true;
        private Thread forwardingThread;
        
        public DebugSession(String sessionId, String podName, WebSocket webSocket, Socket jvmSocket) {
            this.sessionId = sessionId;
            this.podName = podName;
            this.webSocket = webSocket;
            this.jvmSocket = jvmSocket;
        }
        
        public String getSessionId() {
            return sessionId;
        }
        
        /**
         * 启动 JVM -> WebSocket 的转发
         */
        public void startForwarding() {
            forwardingThread = new Thread(() -> {
                try (InputStream in = jvmSocket.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    
                    while (running && (bytesRead = in.read(buffer)) != -1) {
                        // 将 JVM 的响应发送回 WebSocket 客户端
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);
                        webSocket.send(data);
                    }
                } catch (IOException e) {
                    if (running) {
                        logger.error("Session {}: Error reading from JVM: {}", sessionId, e.getMessage());
                    }
                } finally {
                    close();
                }
            }, "JVM-to-WebSocket-" + sessionId);
            
            forwardingThread.setDaemon(true);
            forwardingThread.start();
        }
        
        /**
         * 转发数据到 JVM (WebSocket -> JVM 方向)
         */
        public void forwardToJvm(byte[] data) throws IOException {
            if (!running) {
                throw new IOException("Session closed");
            }
            
            OutputStream out = jvmSocket.getOutputStream();
            out.write(data);
            out.flush();
        }
        
        /**
         * 关闭会话
         */
        public void close() {
            running = false;
            
            try {
                jvmSocket.close();
            } catch (IOException e) {
                logger.debug("Error closing JVM socket: {}", e.getMessage());
            }
            
            if (forwardingThread != null) {
                forwardingThread.interrupt();
            }
        }
    }
}
