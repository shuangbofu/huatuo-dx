package top.fusb.huatuo.dx.idea.settings;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class HuatuoPluginConfigurable implements Configurable {

    private HuatuoPluginSettingsComponent component;

    @Override
    public @Nls String getDisplayName() {
        return "Huatuo Diagnose";
    }

    @Override
    public @Nullable JComponent createComponent() {
        component = new HuatuoPluginSettingsComponent();
        return component.getPanel();
    }

    @Override
    public boolean isModified() {
        HuatuoPluginSettingsState settings = HuatuoPluginSettingsState.getInstance();
        return !settings.getServerUrl().equals(component.getServerUrl().trim())
                || !settings.getUsername().equals(component.getUsername().trim())
                || !settings.getPassword().equals(component.getPassword().trim())
                || !settings.getAccessKey().equals(component.getAccessKey().trim())
                || !settings.getAccessSecret().equals(component.getAccessSecret().trim())
                || !settings.getDefaultProcessName().equals(component.getDefaultProcessName().trim())
                || settings.isEnabledByDefault() != component.isEnabledByDefault();
    }

    @Override
    public void apply() {
        HuatuoPluginSettingsState settings = HuatuoPluginSettingsState.getInstance();
        settings.setServerUrl(component.getServerUrl().trim());
        settings.setUsername(component.getUsername().trim());
        settings.setPassword(component.getPassword().trim());
        settings.setAccessKey(component.getAccessKey().trim());
        settings.setAccessSecret(component.getAccessSecret().trim());
        settings.setDefaultProcessName(component.getDefaultProcessName().trim());
        settings.setEnabledByDefault(component.isEnabledByDefault());
    }

    @Override
    public void reset() {
        HuatuoPluginSettingsState settings = HuatuoPluginSettingsState.getInstance();
        component.setServerUrl(settings.getServerUrl());
        component.setUsername(settings.getUsername());
        component.setPassword(settings.getPassword());
        component.setAccessKey(settings.getAccessKey());
        component.setAccessSecret(settings.getAccessSecret());
        component.setDefaultProcessName(settings.getDefaultProcessName());
        component.setEnabledByDefault(settings.isEnabledByDefault());
    }

    @Override
    public void disposeUIResources() {
        component = null;
    }
}
