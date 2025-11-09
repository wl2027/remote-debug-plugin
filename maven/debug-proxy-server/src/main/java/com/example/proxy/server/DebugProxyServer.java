package com.example.proxy.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Debug Proxy Server
 * Receives connections from debug-proxy-client, parses routing information,
 * and forwards to the target pod (or local debug port)
 */
public class DebugProxyServer {
    
    private static final int DEFAULT_PORT = 18888;
    private static final String ROUTING_HEADER = "X-DEBUG-ROUTE";
    
    private final int port;
    private volatile boolean running = true;
    
    public DebugProxyServer(int port) {
        this.port = port;
    }
    
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        DebugProxyServer server = new DebugProxyServer(port);
        server.start();
    }
    
    public void start() {
        System.out.println("Debug Proxy Server starting on port " + port + "...");
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Debug Proxy Server started successfully!");
            System.out.println("Waiting for connections...");
            
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Received connection from: " + clientSocket.getRemoteSocketAddress());
                
                // Handle each connection in a separate thread
                Thread handler = new Thread(new ConnectionHandler(clientSocket));
                handler.setDaemon(true);
                handler.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handles individual debug connections
     */
    private static class ConnectionHandler implements Runnable {
        private final Socket clientSocket;
        
        public ConnectionHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }
        
        @Override
        public void run() {
            try {
                // Read routing information from custom handshake
                Map<String, String> routingInfo = parseRoutingInfo(clientSocket);
                
                String targetHost = routingInfo.getOrDefault("targetHost", "localhost");
                int targetPort = Integer.parseInt(routingInfo.getOrDefault("targetPort", "5005"));
                String podName = routingInfo.getOrDefault("podName", "unknown");
                
                System.out.println("Routing to pod: " + podName + " at " + targetHost + ":" + targetPort);
                
                // Connect to target pod/application
                try (Socket targetSocket = new Socket(targetHost, targetPort)) {
                    System.out.println("Connected to target: " + targetHost + ":" + targetPort);
                    
                    // Send acknowledgment back to client
                    DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                    out.writeUTF("OK");
                    out.flush();
                    
                    // Start bidirectional forwarding
                    Thread clientToTarget = new Thread(() -> forward(clientSocket, targetSocket, "Client->Target"));
                    Thread targetToClient = new Thread(() -> forward(targetSocket, clientSocket, "Target->Client"));
                    
                    clientToTarget.start();
                    targetToClient.start();
                    
                    // Wait for both threads to complete
                    clientToTarget.join();
                    targetToClient.join();
                    
                    System.out.println("Connection closed for pod: " + podName);
                } catch (Exception e) {
                    System.err.println("Error connecting to target: " + e.getMessage());
                    e.printStackTrace();
                }
            } catch (Exception e) {
                System.err.println("Error handling connection: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        
        /**
         * Parse routing information from the initial handshake
         */
        private Map<String, String> parseRoutingInfo(Socket socket) throws IOException {
            Map<String, String> info = new HashMap<>();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            
            // Read custom protocol header
            String header = in.readUTF();
            if (!header.equals(ROUTING_HEADER)) {
                throw new IOException("Invalid routing header: " + header);
            }
            
            // Read routing parameters
            int paramCount = in.readInt();
            for (int i = 0; i < paramCount; i++) {
                String key = in.readUTF();
                String value = in.readUTF();
                info.put(key, value);
                System.out.println("  Routing param: " + key + " = " + value);
            }
            
            return info;
        }
        
        /**
         * Forward data between two sockets
         */
        private static void forward(Socket from, Socket to, String direction) {
            try (InputStream in = from.getInputStream();
                 OutputStream out = to.getOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    out.flush();
                }
            } catch (IOException e) {
                // Connection closed or error - this is expected when debugging session ends
                System.out.println(direction + " stream closed");
            }
        }
    }
}

