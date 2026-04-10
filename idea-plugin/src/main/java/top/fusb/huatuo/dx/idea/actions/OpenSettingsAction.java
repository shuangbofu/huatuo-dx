package top.fusb.huatuo.dx.idea.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import org.jetbrains.annotations.NotNull;
import top.fusb.huatuo.dx.idea.settings.HuatuoPluginConfigurable;

public class OpenSettingsAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        ShowSettingsUtil.getInstance().showSettingsDialog(event.getProject(), HuatuoPluginConfigurable.class);
    }
}
