package top.fusb.huatuo.dx.idea.client;

import top.fusb.huatuo.dx.idea.settings.HuatuoPluginSettingsState;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HuatuoManagerClient {

    private static final Pattern CODE_PATTERN = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern DATA_NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    private final HuatuoPluginSettingsState settings;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public HuatuoManagerClient(HuatuoPluginSettingsState settings) {
        this.settings = settings;
    }

    public String createRule(PluginRuleRequest request) throws Exception {
        String authToken = login();
        String body = toJson(request);
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String path = "/api/plugin/diagnostic-rules/import";
        String signature = sign("POST", path, settings.getAccessKey(), String.valueOf(timestamp), nonce, body, settings.getAccessSecret());
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(settings.getServerUrl()) + path))
                .header("Authorization", "Bearer " + authToken)
                .header("Content-Type", "application/json")
                .header("X-Huatuo-Access-Key", settings.getAccessKey())
                .header("X-Huatuo-Timestamp", String.valueOf(timestamp))
                .header("X-Huatuo-Nonce", nonce)
                .header("X-Huatuo-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("服务端调用失败: HTTP " + response.statusCode());
        }
        String code = extract(CODE_PATTERN, response.body(), "");
        String message = unescape(extract(MESSAGE_PATTERN, response.body(), "创建诊断规则失败"));
        if (!"0".equals(code)) {
            throw new IllegalStateException(message);
        }
        String ruleName = unescape(extract(DATA_NAME_PATTERN, response.body(), request.ruleName()));
        return "诊断规则已创建: " + ruleName;
    }

    private String login() throws Exception {
        String path = "/api/auth/plugin/login";
        String body = "{"
                + "\"username\":\"" + escape(settings.getUsername()) + "\","
                + "\"password\":\"" + escape(settings.getPassword()) + "\""
                + "}";
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(settings.getServerUrl()) + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("登录失败: HTTP " + response.statusCode());
        }
        String code = extract(CODE_PATTERN, response.body(), "");
        String message = unescape(extract(MESSAGE_PATTERN, response.body(), "登录失败"));
        if (!"0".equals(code)) {
            throw new IllegalStateException(message);
        }
        String token = extract(TOKEN_PATTERN, response.body(), "");
        if (token.isBlank()) {
            throw new IllegalStateException("登录成功但未获取到令牌");
        }
        return token;
    }

    private String sign(String method, String path, String accessKey, String timestamp, String nonce, String body, String secret) throws Exception {
        String canonical = String.join("\n", method, path, accessKey, timestamp, nonce, sha256Hex(body));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return toHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }

    private String toJson(PluginRuleRequest request) {
        return "{"
                + "\"type\":\"" + escape(request.type()) + "\","
                + "\"ruleName\":\"" + escape(request.ruleName()) + "\","
                + "\"className\":\"" + escape(request.className()) + "\","
                + "\"methodName\":\"" + escape(request.methodName()) + "\","
                + "\"selectedProcessName\":\"" + escape(nullToEmpty(request.selectedProcessName())) + "\","
                + "\"outputExpression\":" + nullable(request.outputExpression()) + ","
                + "\"conditionExpression\":" + nullable(request.conditionExpression()) + ","
                + "\"commandOptions\":" + nullable(request.commandOptions()) + ","
                + "\"stackDepth\":" + numberOrNull(request.stackDepth()) + ","
                + "\"maxMatches\":" + numberOrNull(request.maxMatches()) + ","
                + "\"executionTimeoutMs\":" + numberOrNull(request.executionTimeoutMs()) + ","
                + "\"enabled\":" + (request.enabled() != null && request.enabled()) + ","
                + "\"notes\":\"" + escape(nullToEmpty(request.notes())) + "\""
                + "}";
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? "null" : "\"" + escape(value) + "\"";
    }

    private String numberOrNull(Number value) {
        return value == null ? "null" : value.toString();
    }

    private String escape(String value) {
        return nullToEmpty(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String extract(Pattern pattern, String body, String fallback) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private String trimSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
