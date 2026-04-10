package top.fusb.huatuo.dx.idea.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.APP)
@State(name = "HuatuoPluginSettings", storages = @Storage("huatuo-dx-idea.xml"))
public final class HuatuoPluginSettingsState implements PersistentStateComponent<HuatuoPluginSettingsState.State> {

    private State state = new State();

    public static final class State {
        public String serverUrl = "http://127.0.0.1:8081";
        public String username = "";
        public String password = "";
        public String accessKey = "huatuo-idea";
        public String accessSecret = "change-me";
        public String defaultProcessName = "";
        public boolean enabledByDefault = true;
    }

    public static HuatuoPluginSettingsState getInstance() {
        return com.intellij.openapi.application.ApplicationManager.getApplication().getService(HuatuoPluginSettingsState.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public String getServerUrl() {
        return state.serverUrl == null ? "" : state.serverUrl.trim();
    }

    public void setServerUrl(String serverUrl) {
        state.serverUrl = serverUrl;
    }

    public String getUsername() {
        return state.username == null ? "" : state.username.trim();
    }

    public void setUsername(String username) {
        state.username = username;
    }

    public String getPassword() {
        return state.password == null ? "" : state.password.trim();
    }

    public void setPassword(String password) {
        state.password = password;
    }

    public String getAccessKey() {
        return state.accessKey == null ? "" : state.accessKey.trim();
    }

    public void setAccessKey(String accessKey) {
        state.accessKey = accessKey;
    }

    public String getAccessSecret() {
        return state.accessSecret == null ? "" : state.accessSecret.trim();
    }

    public void setAccessSecret(String accessSecret) {
        state.accessSecret = accessSecret;
    }

    public String getDefaultProcessName() {
        return state.defaultProcessName == null ? "" : state.defaultProcessName.trim();
    }

    public void setDefaultProcessName(String defaultProcessName) {
        state.defaultProcessName = defaultProcessName;
    }

    public boolean isEnabledByDefault() {
        return state.enabledByDefault;
    }

    public void setEnabledByDefault(boolean enabledByDefault) {
        state.enabledByDefault = enabledByDefault;
    }
}
