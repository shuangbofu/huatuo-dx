package top.fusb.huatuo.dx.idea.client;

public record PluginRuleRequest(
        String type,
        String ruleName,
        String className,
        String methodName,
        String selectedProcessName,
        String outputExpression,
        String conditionExpression,
        String commandOptions,
        Integer stackDepth,
        Integer maxMatches,
        Long executionTimeoutMs,
        Boolean enabled,
        String notes
) {
    public static PluginRuleRequest forMethod(String type, String className, String methodName, String defaultProcessName, boolean enabled) {
        return forMethod(type, className, methodName, defaultProcessName, enabled, null);
    }

    public static PluginRuleRequest forMethod(String type, String className, String methodName, String defaultProcessName, boolean enabled, String customRuleName) {
        String normalizedType = type == null ? "WATCH" : type;
        String traceName = customRuleName == null || customRuleName.isBlank() ? className + "#" + methodName + " trace" : customRuleName.trim();
        String stackName = customRuleName == null || customRuleName.isBlank() ? className + "#" + methodName + " stack" : customRuleName.trim();
        String watchName = customRuleName == null || customRuleName.isBlank() ? className + "#" + methodName + " watch" : customRuleName.trim();
        return switch (normalizedType) {
            case "TRACE" -> new PluginRuleRequest(
                    "TRACE",
                    traceName,
                    className,
                    methodName,
                    defaultProcessName,
                    null,
                    null,
                    "--skipJDKMethod false",
                    2,
                    5,
                    30000L,
                    enabled,
                    "由 IDEA 插件创建"
            );
            case "STACK" -> new PluginRuleRequest(
                    "STACK",
                    stackName,
                    className,
                    methodName,
                    defaultProcessName,
                    null,
                    null,
                    null,
                    2,
                    5,
                    30000L,
                    enabled,
                    "由 IDEA 插件创建"
            );
            default -> new PluginRuleRequest(
                    "WATCH",
                    watchName,
                    className,
                    methodName,
                    defaultProcessName,
                    "{params, returnObj, throwExp}",
                    null,
                    null,
                    2,
                    1,
                    30000L,
                    enabled,
                    "由 IDEA 插件创建"
            );
        };
    }
}
