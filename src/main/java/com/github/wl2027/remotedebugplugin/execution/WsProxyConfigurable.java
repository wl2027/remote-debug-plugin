package com.github.wl2027.remotedebugplugin.execution;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * WebSocket Proxy Configuration UI
 * 
 * 配置界面，允许用户设置WebSocket服务器地址、目标应用信息等
 */
public class WsProxyConfigurable extends SettingsEditor<WsProxyConfiguration> {
    
    private final JPanel mainPanel;
    private final JTextField wsServerUrl = new JTextField();
    private final JTextField targetHost = new JTextField();
    private final JTextField targetPort = new JTextField();
    private final JTextField podName = new JTextField();
    private final JTextField localPort = new JTextField();
    private final JBCheckBox autoRestart = new JBCheckBox("Auto restart");
    private final ConfigurationModuleSelector moduleSelector;
    
    public WsProxyConfigurable(Project project) {
        mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.LINE_START;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 1.0;
        gc.insets = JBUI.insets(4, 0, 4, 0);
        
        // WebSocket Server URL
        JPanel wsPanel = UI.PanelFactory.panel(wsServerUrl)
                .withLabel("WebSocket Server URL:")
                .withComment("Example: ws://localhost:18888 or wss://proxy.example.com/debug")
                .createPanel();
        mainPanel.add(wsPanel, gc);
        
        // Target configuration section
        gc.gridy++;
        gc.insets = JBUI.insetsTop(16);
        JLabel targetLabel = new JLabel("Target Application Configuration");
        targetLabel.setFont(targetLabel.getFont().deriveFont(Font.BOLD));
        mainPanel.add(targetLabel, gc);
        
        // Target Host
        gc.gridy++;
        gc.insets = JBUI.insets(4, 0, 4, 0);
        JPanel targetHostPanel = UI.PanelFactory.panel(targetHost)
                .withLabel("Target Host:")
                .withComment("The hostname/IP where the target JVM is running")
                .createPanel();
        mainPanel.add(targetHostPanel, gc);
        
        // Target Port
        gc.gridy++;
        JPanel targetPortPanel = UI.PanelFactory.panel(targetPort)
                .withLabel("Target JDWP Port:")
                .withComment("The JDWP port of the target JVM (e.g., 5005)")
                .createPanel();
        mainPanel.add(targetPortPanel, gc);
        
        // Pod Name
        gc.gridy++;
        JPanel podNamePanel = UI.PanelFactory.panel(podName)
                .withLabel("Pod/Instance Name:")
                .withComment("Identifier for the target instance (optional)")
                .createPanel();
        mainPanel.add(podNamePanel, gc);
        
        // Local configuration section
        gc.gridy++;
        gc.insets = JBUI.insetsTop(16);
        JLabel localLabel = new JLabel("Local Proxy Configuration");
        localLabel.setFont(localLabel.getFont().deriveFont(Font.BOLD));
        mainPanel.add(localLabel, gc);
        
        // Local Port
        gc.gridy++;
        gc.insets = JBUI.insets(4, 0, 4, 0);
        JPanel localPortPanel = UI.PanelFactory.panel(localPort)
                .withLabel("Local Proxy Port:")
                .withComment("IDEA will connect to this local port (e.g., 15005)")
                .createPanel();
        mainPanel.add(localPortPanel, gc);
        
        // Auto Restart
        gc.gridy++;
        mainPanel.add(autoRestart, gc);
        
        // Module Selector
        gc.gridy++;
        gc.insets = JBUI.insetsTop(16);
        ModuleDescriptionsComboBox moduleCombo = new ModuleDescriptionsComboBox();
        moduleCombo.allowEmptySelection(JavaCompilerBundle.message("whole.project"));
        moduleSelector = new ConfigurationModuleSelector(project, moduleCombo);
        
        JPanel modulePanel = UI.PanelFactory.panel(moduleCombo)
                .withLabel("Use module classpath:")
                .withComment("First search for sources of the debugged classes")
                .createPanel();
        mainPanel.add(modulePanel, gc);
        
        // Spacer
        gc.gridy++;
        gc.weighty = 1.0;
        gc.fill = GridBagConstraints.BOTH;
        mainPanel.add(new JPanel(), gc);
        
        // Set default values
        wsServerUrl.setText("ws://localhost:18888");
        targetHost.setText("localhost");
        targetPort.setText("5005");
        podName.setText("my-app");
        localPort.setText("15005");
    }
    
    @Override
    protected void resetEditorFrom(@NotNull WsProxyConfiguration config) {
        wsServerUrl.setText(config.WS_SERVER_URL);
        targetHost.setText(config.TARGET_HOST);
        targetPort.setText(config.TARGET_PORT);
        podName.setText(config.POD_NAME);
        localPort.setText(config.LOCAL_PORT);
        autoRestart.setSelected(config.AUTO_RESTART);
        moduleSelector.reset(config);
    }
    
    @Override
    protected void applyEditorTo(@NotNull WsProxyConfiguration config) throws ConfigurationException {
        config.WS_SERVER_URL = wsServerUrl.getText().trim();
        config.TARGET_HOST = targetHost.getText().trim();
        config.TARGET_PORT = targetPort.getText().trim();
        config.POD_NAME = podName.getText().trim();
        config.LOCAL_PORT = localPort.getText().trim();
        config.AUTO_RESTART = autoRestart.isSelected();
        
        // Validate
        if (config.WS_SERVER_URL.isEmpty()) {
            throw new ConfigurationException("WebSocket Server URL is required");
        }
        if (!config.WS_SERVER_URL.startsWith("ws://") && !config.WS_SERVER_URL.startsWith("wss://")) {
            throw new ConfigurationException("WebSocket Server URL must start with ws:// or wss://");
        }
        if (config.TARGET_HOST.isEmpty()) {
            throw new ConfigurationException("Target Host is required");
        }
        if (config.TARGET_PORT.isEmpty()) {
            throw new ConfigurationException("Target Port is required");
        }
        try {
            int port = Integer.parseInt(config.TARGET_PORT);
            if (port < 1 || port > 65535) {
                throw new ConfigurationException("Target Port must be between 1 and 65535");
            }
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Target Port must be a valid number");
        }
        if (config.LOCAL_PORT.isEmpty()) {
            throw new ConfigurationException("Local Port is required");
        }
        try {
            int port = Integer.parseInt(config.LOCAL_PORT);
            if (port < 1 || port > 65535) {
                throw new ConfigurationException("Local Port must be between 1 and 65535");
            }
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Local Port must be a valid number");
        }
        
        moduleSelector.applyTo(config);
    }
    
    @Override
    protected @NotNull JComponent createEditor() {
        return mainPanel;
    }
}

