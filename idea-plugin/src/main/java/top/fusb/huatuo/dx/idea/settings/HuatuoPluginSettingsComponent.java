package top.fusb.huatuo.dx.idea.settings;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JPanel;

public class HuatuoPluginSettingsComponent {

    private final JPanel panel = new JPanel(new GridBagLayout());
    private final JBTextField serverUrlField = new JBTextField();
    private final JBTextField usernameField = new JBTextField();
    private final JBPasswordField passwordField = new JBPasswordField();
    private final JBTextField accessKeyField = new JBTextField();
    private final JBPasswordField accessSecretField = new JBPasswordField();
    private final JBTextField defaultProcessNameField = new JBTextField();
    private final JBCheckBox enabledByDefaultCheckBox = new JBCheckBox("规则默认启用");

    public HuatuoPluginSettingsComponent() {
        int row = 0;
        row = addRow(row, "服务端地址", serverUrlField);
        row = addRow(row, "用户名", usernameField);
        row = addRow(row, "密码", passwordField);
        row = addRow(row, "访问密钥", accessKeyField);
        row = addRow(row, "签名密钥", accessSecretField);
        row = addRow(row, "默认 Java 进程显示名", defaultProcessNameField);

        GridBagConstraints constraints = baseConstraints(row);
        constraints.gridx = 1;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(enabledByDefaultCheckBox, constraints);

        GridBagConstraints fill = new GridBagConstraints();
        fill.gridx = 0;
        fill.gridy = row + 1;
        fill.weighty = 1;
        fill.fill = GridBagConstraints.VERTICAL;
        panel.add(new JPanel(), fill);
    }

    private int addRow(int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = baseConstraints(row);
        labelConstraints.anchor = GridBagConstraints.WEST;
        panel.add(new JBLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = baseConstraints(row);
        fieldConstraints.gridx = 1;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, fieldConstraints);
        return row + 1;
    }

    private GridBagConstraints baseConstraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.insets = new Insets(6, 6, 6, 6);
        return constraints;
    }

    public JPanel getPanel() {
        return panel;
    }

    public String getServerUrl() {
        return serverUrlField.getText();
    }

    public void setServerUrl(String value) {
        serverUrlField.setText(value);
    }

    public String getUsername() {
        return usernameField.getText();
    }

    public void setUsername(String value) {
        usernameField.setText(value);
    }

    public String getPassword() {
        return new String(passwordField.getPassword());
    }

    public void setPassword(String value) {
        passwordField.setText(value);
    }

    public String getAccessKey() {
        return accessKeyField.getText();
    }

    public void setAccessKey(String value) {
        accessKeyField.setText(value);
    }

    public String getAccessSecret() {
        return new String(accessSecretField.getPassword());
    }

    public void setAccessSecret(String value) {
        accessSecretField.setText(value);
    }

    public String getDefaultProcessName() {
        return defaultProcessNameField.getText();
    }

    public void setDefaultProcessName(String value) {
        defaultProcessNameField.setText(value);
    }

    public boolean isEnabledByDefault() {
        return enabledByDefaultCheckBox.isSelected();
    }

    public void setEnabledByDefault(boolean value) {
        enabledByDefaultCheckBox.setSelected(value);
    }
}
