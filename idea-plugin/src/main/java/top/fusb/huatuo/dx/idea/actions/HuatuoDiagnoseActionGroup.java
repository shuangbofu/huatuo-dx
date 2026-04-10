package top.fusb.huatuo.dx.idea.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

public class HuatuoDiagnoseActionGroup extends ActionGroup implements DumbAware {

    private final AnAction[] children = new AnAction[] {
            new CreateWatchRuleAction(),
            new CreateTraceRuleAction(),
            new CreateStackRuleAction(),
    };

    @Override
    public AnAction @NotNull [] getChildren(AnActionEvent e) {
        return children;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        var project = event.getProject();
        var file = event.getData(CommonDataKeys.PSI_FILE);
        boolean visible = project != null && file instanceof PsiJavaFile;
        event.getPresentation().setVisible(visible);
        event.getPresentation().setEnabled(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
