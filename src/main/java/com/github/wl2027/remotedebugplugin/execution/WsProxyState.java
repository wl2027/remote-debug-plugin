package com.github.wl2027.remotedebugplugin.execution;

import com.github.wl2027.remotedebugplugin.proxy.WsProxyClient;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * WebSocket Proxy State
 * 
 * 管理WebSocket代理客户端的生命周期：
 * 1. 启动本地ServerSocket监听
 * 2. 等待IDEA调试器连接
 * 3. 创建WebSocket连接到代理服务器
 * 4. 转发JDWP数据
 */
public class WsProxyState implements RemoteState {
    
    private static final Logger logger = LoggerFactory.getLogger(WsProxyState.class);
    
    private final Project project;
    private final WsProxyConfiguration configuration;
    private final boolean autoRestart;
    private final RemoteConnection remoteConnection;
    
    private ServerSocket localServer;
    private WsProxyClient proxyClient;
    private Thread proxyThread;
    
    public WsProxyState(Project project, WsProxyConfiguration configuration, boolean autoRestart) {
        this.project = project;
        this.configuration = configuration;
        this.autoRestart = autoRestart;
        this.remoteConnection = configuration.createRemoteConnection();
    }
    
    @Override
    public ExecutionResult execute(final Executor executor, final @NotNull ProgramRunner<?> runner) 
            throws ExecutionException {
        ConsoleViewImpl consoleView = new ConsoleViewImpl(project, false);
        
        // 创建自定义的ProcessHandler
        WsProxyProcessHandler processHandler = new WsProxyProcessHandler(project, autoRestart);
        
        consoleView.attachToProcess(processHandler);
        
        // 启动WebSocket代理客户端（不立即调用startNotify）
        startProxyClient(consoleView, processHandler);
        
        return new DefaultExecutionResult(consoleView, processHandler);
    }
    
    /**
     * 启动WebSocket代理客户端
     */
    private void startProxyClient(ConsoleViewImpl consoleView, WsProxyProcessHandler processHandler) 
            throws ExecutionException {
        try {
            int localPort = Integer.parseInt(configuration.LOCAL_PORT);
            
            // 启动本地ServerSocket，设置 SO_REUSEADDR 避免端口占用问题
            localServer = new ServerSocket();
            localServer.setReuseAddress(true);  // 允许端口重用
            localServer.bind(new java.net.InetSocketAddress("localhost", localPort));
            
            consoleView.print("WebSocket Proxy Client starting...\n", 
                             ConsoleViewContentType.SYSTEM_OUTPUT);
            consoleView.print("Proxy Server: " + configuration.WS_SERVER_URL + "\n", 
                             ConsoleViewContentType.SYSTEM_OUTPUT);
            consoleView.print("Target: " + configuration.TARGET_HOST + ":" + 
                             configuration.TARGET_PORT + "\n", 
                             ConsoleViewContentType.SYSTEM_OUTPUT);
            consoleView.print("Local Port: " + localPort + "\n", 
                             ConsoleViewContentType.SYSTEM_OUTPUT);
            consoleView.print("Waiting for debugger connection on port " + localPort + "...\n", 
                             ConsoleViewContentType.SYSTEM_OUTPUT);
            
            logger.info("WebSocket proxy client started on port {}", localPort);
            
            // 在后台线程等待IDEA连接
            proxyThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted() && !localServer.isClosed()) {
                        Socket jdiSocket = localServer.accept();
                        
                        consoleView.print("Debugger connected from: " + 
                                         jdiSocket.getRemoteSocketAddress() + "\n", 
                                         ConsoleViewContentType.SYSTEM_OUTPUT);
                        logger.info("Debugger connected from: {}", jdiSocket.getRemoteSocketAddress());
                        
                        // 创建并启动代理客户端
                        proxyClient = new WsProxyClient(
                                configuration.WS_SERVER_URL,
                                configuration.TARGET_HOST,
                                Integer.parseInt(configuration.TARGET_PORT),
                                configuration.POD_NAME
                        );
                        
                        consoleView.print("Connecting to proxy server...\n", 
                                         ConsoleViewContentType.SYSTEM_OUTPUT);
                        
                        try {
                            proxyClient.connect(jdiSocket);
                            consoleView.print("Connected to proxy server successfully!\n", 
                                             ConsoleViewContentType.SYSTEM_OUTPUT);
                            consoleView.print("Debug session established. Session ID: " + 
                                             proxyClient.getSessionId() + "\n", 
                                             ConsoleViewContentType.SYSTEM_OUTPUT);
                        } catch (Exception e) {
                            consoleView.print("Failed to connect to proxy server: " + 
                                             e.getMessage() + "\n", 
                                             ConsoleViewContentType.ERROR_OUTPUT);
                            logger.error("Failed to connect to proxy server", e);
                            jdiSocket.close();
                        }
                        
                        // 如果不自动重启，则退出循环
                        if (!autoRestart) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        consoleView.print("Proxy error: " + e.getMessage() + "\n", 
                                         ConsoleViewContentType.ERROR_OUTPUT);
                        logger.error("Proxy error", e);
                    }
                } finally {
                    processHandler.destroyProcess();
                }
            }, "WsProxyClient-Listener");
            
            proxyThread.setDaemon(true);
            proxyThread.start();
            
            // 通知ProcessHandler已启动
            processHandler.startNotify();
            
        } catch (java.net.BindException e) {
            String message = "Port " + configuration.LOCAL_PORT + " is already in use. " +
                           "Please stop the previous debug session or choose a different port.";
            logger.error("Failed to bind to port {}: {}", configuration.LOCAL_PORT, e.getMessage());
            throw new ExecutionException(message, e);
        } catch (IOException e) {
            throw new ExecutionException("Failed to start proxy client: " + e.getMessage(), e);
        } catch (NumberFormatException e) {
            throw new ExecutionException("Invalid port number: " + configuration.LOCAL_PORT, e);
        }
    }
    
    @Override
    public RemoteConnection getRemoteConnection() {
        return remoteConnection;
    }
    
    /**
     * 停止代理客户端
     */
    public void stop() {
        logger.info("Stopping WebSocket proxy client...");
        
        // 先中断监听线程
        if (proxyThread != null && proxyThread.isAlive()) {
            logger.debug("Interrupting proxy thread");
            proxyThread.interrupt();
        }
        
        // 关闭代理客户端
        if (proxyClient != null) {
            logger.debug("Closing proxy client");
            proxyClient.close();
            proxyClient = null;
        }
        
        // 关闭本地服务器（这会导致 accept() 抛出异常，从而退出循环）
        if (localServer != null && !localServer.isClosed()) {
            try {
                logger.debug("Closing local server on port {}", localServer.getLocalPort());
                localServer.close();
            } catch (IOException e) {
                logger.error("Error closing local server", e);
            } finally {
                localServer = null;
            }
        }
        
        logger.info("WebSocket proxy client stopped");
    }
    
    /**
     * 自定义ProcessHandler for WebSocket proxy
     * 不依赖于DebugProcess，避免NPE问题
     */
    private class WsProxyProcessHandler extends ProcessHandler {
        private final Project myProject;
        private final boolean myAutoRestart;
        
        public WsProxyProcessHandler(Project project, boolean autoRestart) {
            this.myProject = project;
            this.myAutoRestart = autoRestart;
        }
        
        @Override
        protected void destroyProcessImpl() {
            logger.info("Destroying WebSocket proxy process");
            stop();
            notifyProcessTerminated(0);
        }
        
        @Override
        protected void detachProcessImpl() {
            logger.info("Detaching WebSocket proxy process");
            stop();
            notifyProcessDetached();
        }
        
        @Override
        public boolean detachIsDefault() {
            return true;
        }
        
        @Override
        @Nullable
        public OutputStream getProcessInput() {
            return null;
        }
    }
}


