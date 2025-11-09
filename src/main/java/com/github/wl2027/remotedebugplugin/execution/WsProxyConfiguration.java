package com.github.wl2027.remotedebugplugin.execution;

import com.intellij.debugger.engine.RemoteStateState;
import com.intellij.debugger.impl.GenericDebuggerRunnerSettings;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * WebSocket Proxy Remote Configuration
 * 
 * 配置数据类，保存WebSocket代理调试所需的所有参数
 */
public class WsProxyConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule, Element>
        implements RunConfigurationWithSuppressedDefaultRunAction, RemoteRunProfile {
    
    // WebSocket代理服务器配置
    public String WS_SERVER_URL = "ws://localhost:18888";
    
    // 目标应用配置
    public String TARGET_HOST = "localhost";
    public String TARGET_PORT = "5005";
    public String POD_NAME = "my-app";
    
    // 本地JDWP端口（IDEA连接到这个端口）
    public String LOCAL_PORT = "15005";
    
    // 自动重连
    public boolean AUTO_RESTART = false;
    
    public WsProxyConfiguration(final Project project, ConfigurationFactory configurationFactory) {
        super(new JavaRunConfigurationModule(project, true), configurationFactory);
    }
    
    @Override
    public void writeExternal(final @NotNull Element element) throws WriteExternalException {
        super.writeExternal(element);
        DefaultJDOMExternalizer.writeExternal(this, element);
    }
    
    @Override
    public void readExternal(final @NotNull Element element) throws InvalidDataException {
        super.readExternal(element);
        DefaultJDOMExternalizer.readExternal(this, element);
    }
    
    /**
     * 创建RemoteConnection用于调试器连接
     * IDEA将连接到本地代理客户端的端口
     */
    public RemoteConnection createRemoteConnection() {
        // IDEA作为客户端，连接到本地代理的端口
        // 所以这里使用localhost和本地端口
        return new RemoteConnection(
                true,          // 使用Socket传输
                "localhost",   // 连接到本地
                LOCAL_PORT,    // 本地代理端口
                false          // IDEA作为客户端(attach模式)
        );
    }
    
    @Override
    public RunProfileState getState(final @NotNull Executor executor, final @NotNull ExecutionEnvironment env) 
            throws ExecutionException {
        final GenericDebuggerRunnerSettings debuggerSettings = (GenericDebuggerRunnerSettings)env.getRunnerSettings();
        if (debuggerSettings != null) {
            debuggerSettings.LOCAL = false;
            debuggerSettings.setDebugPort(LOCAL_PORT);
            debuggerSettings.setTransport(DebuggerSettings.SOCKET_TRANSPORT);
        }
        
        // 使用自定义的WsProxyState来启动代理客户端
        return new WsProxyState(getProject(), this, AUTO_RESTART);
    }
    
    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        SettingsEditorGroup<WsProxyConfiguration> group = new SettingsEditorGroup<>();
        group.addEditor("Configuration", new WsProxyConfigurable(getProject()));
        return group;
    }
    
    @Override
    public @Unmodifiable Collection<Module> getValidModules() {
        return getAllModules();
    }
}

