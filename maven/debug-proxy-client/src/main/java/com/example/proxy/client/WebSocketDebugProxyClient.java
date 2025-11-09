package com.example.proxy.client;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 简化版 WebSocket Debug Proxy Client
 * 移除认证机制，专注于 JDWP 协议转发
 * 
 * 在本地监听 JDWP 端口，接收 JDI 的调试连接，通过 WebSocket 转发到远程的 proxy-server
 * 
 * 架构: JDI <--JDWP--> proxy-client <--WebSocket--> proxy-server <--JDWP--> JVM
 */
public class WebSocketDebugProxyClient {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketDebugProxyClient.class);
    private static final int DEFAULT_LOCAL_PORT = 15005;
    
    private final int localPort;
    private final String serverUrl;
    private final Map<String, String> targetInfo;
    private volatile boolean running = true;
    
    public WebSocketDebugProxyClient(int localPort, String serverUrl, String targetHost, int targetPort, String podName) {
        this.localPort = localPort;
        this.serverUrl = serverUrl;
        
        this.targetInfo = new HashMap<>();
        this.targetInfo.put("targetHost", targetHost);
        this.targetInfo.put("targetPort", String.valueOf(targetPort));
        this.targetInfo.put("podName", podName != null ? podName : "unknown");
    }
    
    public static void main(String[] args) {
        // 测试用的默认参数
        if (args.length == 0 || args[0].isEmpty()) {
            args = new String[]{"ws://127.0.0.1:18888", "127.0.0.1", "15006", "15005", "test-app"};
        }
        
        if (args.length < 3) {
            System.out.println("Usage: java WebSocketDebugProxyClient <server-url> <target-host> <target-port> [local-port] [pod-name]");
            System.out.println("Example: java WebSocketDebugProxyClient ws://localhost:18888 localhost 5005 15005 my-pod");
            System.exit(1);
        }
        
        String serverUrl = args[0];
        String targetHost = args[1];
        int targetPort = Integer.parseInt(args[2]);
        int localPort = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_LOCAL_PORT;
        String podName = args.length > 4 ? args[4] : null;
        
        WebSocketDebugProxyClient client = new WebSocketDebugProxyClient(
            localPort, serverUrl, targetHost, targetPort, podName
        );
        
        client.start();
    }
    
    public void start() {
        logger.info("WebSocket Debug Proxy Client starting...");
        logger.info("  Local port: {}", localPort);
        logger.info("  Server URL: {}", serverUrl);
        logger.info("  Target: {}:{}", targetInfo.get("targetHost"), targetInfo.get("targetPort"));
        logger.info("  Pod: {}", targetInfo.get("podName"));
        
        try (ServerSocket serverSocket = new ServerSocket(localPort)) {
            logger.info("Local JDWP server started on port {}", localPort);
            logger.info("JDI/IDEA can now connect to localhost:{}", localPort);
            
            while (running) {
                Socket jdiSocket = serverSocket.accept();
                logger.info("JDI debugger connected from: {}", jdiSocket.getRemoteSocketAddress());
                
                // 为每个调试连接创建独立的 WebSocket 连接
                Thread handler = new Thread(new DebugSessionHandler(jdiSocket));
                handler.setDaemon(true);
                handler.start();
            }
        } catch (IOException e) {
            logger.error("Client error: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 处理单个调试会话
     */
    private class DebugSessionHandler implements Runnable {
        private final Socket jdiSocket;
        private final String sessionId;
        
        public DebugSessionHandler(Socket jdiSocket) {
            this.jdiSocket = jdiSocket;
            this.sessionId = UUID.randomUUID().toString();
        }
        
        @Override
        public void run() {
            WebSocketClient wsClient = null;
            try {
                // 构建 WebSocket URI
                URI serverUri = new URI(serverUrl);
                
                // 创建 WebSocket 客户端
                wsClient = createWebSocketClient(serverUri);
                
                // 连接到服务器
                logger.info("Session {}: Connecting to proxy server...", sessionId);
                boolean connected = wsClient.connectBlocking();
                
                if (!connected) {
                    logger.error("Session {}: Failed to connect to proxy server", sessionId);
                    jdiSocket.close();
                    return;
                }
                
                logger.info("Session {}: Connected to proxy server", sessionId);
                logger.info("Session {}: Debug session established", sessionId);
                
                // 启动 JDI -> WebSocket 转发
                forwardJdiToWebSocket(jdiSocket, wsClient);
                
            } catch (Exception e) {
                logger.error("Session {}: Error: {}", sessionId, e.getMessage(), e);
            } finally {
                if (wsClient != null) {
                    wsClient.close();
                }
                try {
                    jdiSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
                logger.info("Session {}: Closed", sessionId);
            }
        }
        
        /**
         * 创建 WebSocket 客户端
         */
        private WebSocketClient createWebSocketClient(URI serverUri) {
            return new WebSocketClient(serverUri, createHeaders()) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    logger.info("Session {}: WebSocket connection opened", sessionId);
                }
                
                @Override
                public void onMessage(String message) {
                    // 处理文本消息（控制命令）
                    logger.debug("Session {}: Received text message: {}", sessionId, message);
                }
                
                @Override
                public void onMessage(ByteBuffer bytes) {
                    // 接收来自 server 的 JDWP 数据，转发到 JDI
                    try {
                        byte[] data = new byte[bytes.remaining()];
                        bytes.get(data);
                        OutputStream out = jdiSocket.getOutputStream();
                        out.write(data);
                        out.flush();
                    } catch (IOException e) {
                        logger.error("Session {}: Error forwarding to JDI: {}", 
                                   sessionId, e.getMessage());
                        close();
                    }
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.info("Session {}: WebSocket closed: code={}, reason={}, remote={}", 
                               sessionId, code, reason, remote);
                }
                
                @Override
                public void onError(Exception ex) {
                    logger.error("Session {}: WebSocket error: {}", sessionId, ex.getMessage(), ex);
                }
            };
        }
        
        /**
         * 创建 WebSocket 握手 headers
         */
        private Map<String, String> createHeaders() {
            Map<String, String> headers = new HashMap<>();
            
            // 添加目标信息
            headers.put("X-Target-Host", targetInfo.get("targetHost"));
            headers.put("X-Target-Port", targetInfo.get("targetPort"));
            headers.put("X-Pod-Name", targetInfo.get("podName"));
            headers.put("X-Session-Id", sessionId);
            
            return headers;
        }
        
        /**
         * 转发 JDI 的数据到 WebSocket
         */
        private void forwardJdiToWebSocket(Socket jdiSocket, WebSocketClient wsClient) {
            try (InputStream in = jdiSocket.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    // 将 JDI 的 JDWP 请求发送到 WebSocket
                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);
                    wsClient.send(data);
                }
            } catch (IOException e) {
                logger.info("Session {}: JDI connection closed", sessionId);
            }
        }
    }
    
    public void stop() {
        running = false;
    }
}
