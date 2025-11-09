package com.example.proxy.client;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Debug Proxy Client
 * Accepts connections from IDEA/JDI debugger, adds custom routing parameters,
 * and forwards to debug-proxy-server
 */
public class DebugProxyClient {
    
    private static final int DEFAULT_LOCAL_PORT = 15005;
    private static final String ROUTING_HEADER = "X-DEBUG-ROUTE";
    
    private final int localPort;
    private final String serverHost;
    private final int serverPort;
    private final Map<String, String> routingParams;
    private volatile boolean running = true;
    
    public DebugProxyClient(int localPort, String serverHost, int serverPort) {
        this.localPort = localPort;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.routingParams = new HashMap<>();
    }
    
    public void addRoutingParam(String key, String value) {
        routingParams.put(key, value);
    }
    
    public static void main(String[] args) {
        args = new String[]{"127.0.01","18888","15005","my-pod","localhost","15006"};
        if (args.length < 2) {
            System.out.println("Usage: java DebugProxyClient <server-host> <server-port> [local-port] [podName] [targetHost] [targetPort]");
            System.out.println("Example: java DebugProxyClient localhost 8888 5005 my-pod localhost 5006");
            System.exit(1);
        }
        
        String serverHost = args[0];
        int serverPort = Integer.parseInt(args[1]);
        int localPort = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_LOCAL_PORT;
        
        DebugProxyClient client = new DebugProxyClient(localPort, serverHost, serverPort);
        
        // Add routing parameters
        if (args.length > 3) {
            client.addRoutingParam("podName", args[3]);
        }
        if (args.length > 4) {
            client.addRoutingParam("targetHost", args[4]);
        }
        if (args.length > 5) {
            client.addRoutingParam("targetPort", args[5]);
        }
        
        client.start();
    }
    
    public void start() {
        System.out.println("Debug Proxy Client starting...");
        System.out.println("  Local port: " + localPort);
        System.out.println("  Server: " + serverHost + ":" + serverPort);
        System.out.println("  Routing params: " + routingParams);
        
        try (ServerSocket serverSocket = new ServerSocket(localPort)) {
            System.out.println("Debug Proxy Client started successfully!");
            System.out.println("IDEA/Debugger can now connect to localhost:" + localPort);
            
            while (running) {
                Socket debuggerSocket = serverSocket.accept();
                System.out.println("Debugger connected from: " + debuggerSocket.getRemoteSocketAddress());
                
                // Handle connection in separate thread
                Thread handler = new Thread(new ConnectionHandler(debuggerSocket, serverHost, serverPort, routingParams));
                handler.setDaemon(true);
                handler.start();
            }
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handles individual debugger connections
     */
    private static class ConnectionHandler implements Runnable {
        private final Socket debuggerSocket;
        private final String serverHost;
        private final int serverPort;
        private final Map<String, String> routingParams;
        
        public ConnectionHandler(Socket debuggerSocket, String serverHost, int serverPort, Map<String, String> routingParams) {
            this.debuggerSocket = debuggerSocket;
            this.serverHost = serverHost;
            this.serverPort = serverPort;
            this.routingParams = new HashMap<>(routingParams);
        }
        
        @Override
        public void run() {
            try {
                // Connect to proxy server
                System.out.println("Connecting to proxy server: " + serverHost + ":" + serverPort);
                Socket serverSocket = new Socket(serverHost, serverPort);
                System.out.println("Connected to proxy server");
                
                // Send routing information using custom protocol
                sendRoutingInfo(serverSocket);
                
                // Wait for server acknowledgment
                DataInputStream in = new DataInputStream(serverSocket.getInputStream());
                String ack = in.readUTF();
                if (!"OK".equals(ack)) {
                    throw new IOException("Server rejected connection: " + ack);
                }
                System.out.println("Server acknowledged connection");
                
                // Start bidirectional forwarding between debugger and server
                Thread debuggerToServer = new Thread(() -> forward(debuggerSocket, serverSocket, "Debugger->Server"));
                Thread serverToDebugger = new Thread(() -> forward(serverSocket, debuggerSocket, "Server->Debugger"));
                
                debuggerToServer.start();
                serverToDebugger.start();
                
                // Wait for both threads to complete
                debuggerToServer.join();
                serverToDebugger.join();
                
                System.out.println("Debug session ended");
                
                serverSocket.close();
            } catch (Exception e) {
                System.err.println("Error handling connection: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    debuggerSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        
        /**
         * Send routing information to proxy server
         */
        private void sendRoutingInfo(Socket serverSocket) throws IOException {
            DataOutputStream out = new DataOutputStream(serverSocket.getOutputStream());
            
            // Send custom protocol header
            out.writeUTF(ROUTING_HEADER);
            
            // Send routing parameters
            out.writeInt(routingParams.size());
            for (Map.Entry<String, String> entry : routingParams.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeUTF(entry.getValue());
                System.out.println("  Sent routing param: " + entry.getKey() + " = " + entry.getValue());
            }
            
            out.flush();
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
                // Connection closed - this is expected when debugging session ends
                System.out.println(direction + " stream closed");
            }
        }
    }
}

