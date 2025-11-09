package com.github.wl2027.remotedebugplugin.execution;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

/**
 * WebSocket Proxy Debug Configuration Type
 * 
 * 定义新的Run/Debug Configuration类型，支持通过WebSocket代理进行远程调试
 */
public final class WsProxyConfigurationType extends SimpleConfigurationType implements DumbAware {
    
    public WsProxyConfigurationType() {
        super("WsProxyRemote", 
              "Remote JVM Debug (WebSocket)", 
              "Debug Java applications via WebSocket proxy",
              NotNullLazyValue.createValue(() -> AllIcons.RunConfigurations.Remote));
    }
    
    @Override
    public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new WsProxyConfiguration(project, this);
    }
    
    @Override
    public @NotNull String getTag() {
        return "wsProxyRemote";
    }
    
    @Override
    public String getHelpTopic() {
        return "reference.dialogs.rundebug.WsProxyRemote";
    }
    
    public static @NotNull WsProxyConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(WsProxyConfigurationType.class);
    }
    
    @Override
    public boolean isEditableInDumbMode() {
        return true;
    }
    
    @Override
    public boolean isDumbAware() {
        return true;
    }
    
    @Deprecated(forRemoval = true)
    public @NotNull ConfigurationFactory getFactory() {
        return this;
    }
}

