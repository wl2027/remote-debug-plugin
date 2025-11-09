package com.github.wl2027.remotedebugplugin.proxy;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket Debug Proxy Client for IDEA Plugin
 * 
 * 连接到WebSocket服务器，转发JDWP协议数据
 */
public class WsProxyClient {
    
    private static final Logger logger = LoggerFactory.getLogger(WsProxyClient.class);
    
    private final String serverUrl;
    private final String targetHost;
    private final int targetPort;
    private final String podName;
    private final String sessionId;
    
    private WebSocketClient wsClient;
    private Socket jdiSocket;
    private volatile boolean running = false;
    
    public WsProxyClient(String serverUrl, String targetHost, int targetPort, String podName) {
        this.serverUrl = serverUrl;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.podName = podName != null && !podName.isEmpty() ? podName : "idea-debug";
        this.sessionId = UUID.randomUUID().toString();
    }
    
    /**
     * 连接到代理服务器
     * @param jdiSocket JDI连接的socket
     */
    public void connect(Socket jdiSocket) throws Exception {
        this.jdiSocket = jdiSocket;
        this.running = true;
        
        URI serverUri = new URI(serverUrl);
        
        // 创建WebSocket客户端
        wsClient = createWebSocketClient(serverUri);
        
        // 连接到服务器
        logger.info("Session {}: Connecting to proxy server at {}", sessionId, serverUrl);
        boolean connected = wsClient.connectBlocking();
        
        if (!connected) {
            throw new IOException("Failed to connect to proxy server: " + serverUrl);
        }
        
        logger.info("Session {}: Connected to proxy server", sessionId);
        
        // 启动JDI -> WebSocket转发
        forwardJdiToWebSocket();
    }
    
    /**
     * 创建WebSocket客户端
     */
    private WebSocketClient createWebSocketClient(URI serverUri) {
        return new WebSocketClient(serverUri, createHeaders()) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                logger.info("Session {}: WebSocket connection opened", sessionId);
            }
            
            @Override
            public void onMessage(String message) {
                logger.debug("Session {}: Received text message: {}", sessionId, message);
            }
            
            @Override
            public void onMessage(ByteBuffer bytes) {
                // 接收来自server的JDWP数据，转发到JDI
                try {
                    byte[] data = new byte[bytes.remaining()];
                    bytes.get(data);
                    OutputStream out = jdiSocket.getOutputStream();
                    out.write(data);
                    out.flush();
                } catch (IOException e) {
                    logger.error("Session {}: Error forwarding to JDI: {}", sessionId, e.getMessage());
                    close();
                }
            }
            
            @Override
            public void onClose(int code, String reason, boolean remote) {
                logger.info("Session {}: WebSocket closed: code={}, reason={}, remote={}", 
                           sessionId, code, reason, remote);
                running = false;
            }
            
            @Override
            public void onError(Exception ex) {
                logger.error("Session {}: WebSocket error: {}", sessionId, ex.getMessage(), ex);
                running = false;
            }
        };
    }
    
    /**
     * 创建WebSocket握手headers
     */
    private Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Target-Host", targetHost);
        headers.put("X-Target-Port", String.valueOf(targetPort));
        headers.put("X-Pod-Name", podName);
        headers.put("X-Session-Id", sessionId);
        return headers;
    }
    
    /**
     * 转发JDI的数据到WebSocket
     */
    private void forwardJdiToWebSocket() {
        try (InputStream in = jdiSocket.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while (running && (bytesRead = in.read(buffer)) != -1) {
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);
                wsClient.send(data);
            }
        } catch (IOException e) {
            logger.info("Session {}: JDI connection closed: {}", sessionId, e.getMessage());
        }
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        running = false;
        if (wsClient != null) {
            wsClient.close();
        }
        if (jdiSocket != null) {
            try {
                jdiSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        logger.info("Session {}: Proxy client closed", sessionId);
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public String getSessionId() {
        return sessionId;
    }
}

