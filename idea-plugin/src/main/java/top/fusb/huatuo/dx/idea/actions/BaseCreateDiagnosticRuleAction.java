package top.fusb.huatuo.dx.idea.actions;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.DumbAware;
import top.fusb.huatuo.dx.idea.client.HuatuoManagerClient;
import top.fusb.huatuo.dx.idea.client.PluginRuleRequest;
import top.fusb.huatuo.dx.idea.settings.HuatuoPluginSettingsState;

public abstract class BaseCreateDiagnosticRuleAction extends AnAction implements DumbAware {

    private final String diagnosticType;

    protected BaseCreateDiagnosticRuleAction(String diagnosticType, String text) {
        super(text);
        this.diagnosticType = diagnosticType;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        boolean visible = project != null && file instanceof PsiJavaFile;
        event.getPresentation().setVisible(visible);
        event.getPresentation().setEnabled(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        PsiMethod method = findMethod(event);
        if (project == null || method == null || method.getContainingClass() == null || method.getContainingClass().getQualifiedName() == null) {
            Messages.showInfoMessage(project, "请先把光标放到具体的方法上，再使用华佗诊断。", "华佗诊断");
            return;
        }
        HuatuoPluginSettingsState settings = HuatuoPluginSettingsState.getInstance();
        if (settings.getServerUrl().isBlank()
                || settings.getUsername().isBlank()
                || settings.getPassword().isBlank()
                || settings.getAccessKey().isBlank()
                || settings.getAccessSecret().isBlank()) {
            Messages.showWarningDialog(project, "请先在设置中填写服务端地址、用户名、密码、访问密钥和签名密钥。", "华佗诊断");
            return;
        }
        String className = method.getContainingClass().getQualifiedName();
        String methodName = method.getName();
        String defaultRuleName = className + "#" + methodName + " " + diagnosticType.toLowerCase();
        String ruleName = Messages.showInputDialog(
                project,
                "请输入规则名称",
                "华佗诊断",
                Messages.getQuestionIcon(),
                defaultRuleName,
                null
        );
        if (ruleName == null || ruleName.isBlank()) {
            return;
        }
        PluginRuleRequest request = PluginRuleRequest.forMethod(
                diagnosticType,
                className,
                methodName,
                settings.getDefaultProcessName(),
                settings.isEnabledByDefault(),
                ruleName.trim()
        );
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            try {
                String response = new HuatuoManagerClient(settings).createRule(request);
                notify(project, NotificationType.INFORMATION, response);
            } catch (Exception exception) {
                notify(project, NotificationType.ERROR, exception.getMessage() == null ? "创建诊断规则失败" : exception.getMessage());
            }
        }, "创建华佗诊断规则", false, project);
    }

    private void notify(Project project, NotificationType type, String content) {
        ApplicationManager.getApplication().invokeLater(() ->
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("Huatuo Diagnose")
                        .createNotification(content, type)
                        .notify(project));
    }

    private PsiMethod findMethod(AnActionEvent event) {
        PsiElement element = event.getData(CommonDataKeys.PSI_ELEMENT);
        if (element == null) {
            PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
            var editor = event.getData(CommonDataKeys.EDITOR);
            if (file != null && editor != null) {
                element = file.findElementAt(editor.getCaretModel().getOffset());
            }
        }
        if (element == null) {
            return null;
        }
        return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    }
}
