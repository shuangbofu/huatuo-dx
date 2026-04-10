package top.fusb.huatuo.dx.manager.service;

import top.fusb.huatuo.dx.manager.config.ManagerProperties;
import top.fusb.huatuo.dx.manager.exception.BusinessException;
import top.fusb.huatuo.dx.manager.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class PluginSignatureService {

    private static final String H_ACCESS_KEY = "X-Huatuo-Access-Key";
    private static final String H_TIMESTAMP = "X-Huatuo-Timestamp";
    private static final String H_NONCE = "X-Huatuo-Nonce";
    private static final String H_SIGNATURE = "X-Huatuo-Signature";

    private final ManagerProperties managerProperties;
    private final Map<String, Long> usedNonces = new ConcurrentHashMap<>();

    public PluginSignatureService(ManagerProperties managerProperties) {
        this.managerProperties = managerProperties;
    }

    public void verify(HttpServletRequest request, String body) {
        ManagerProperties.Plugin plugin = managerProperties.getPlugin();
        if (!plugin.isEnabled()) {
            throw new BusinessException(ErrorCode.PLUGIN_ACCESS_DENIED, "IDEA 插件接入未启用");
        }
        String accessKey = requiredHeader(request, H_ACCESS_KEY);
        String timestamp = requiredHeader(request, H_TIMESTAMP);
        String nonce = requiredHeader(request, H_NONCE);
        String signature = requiredHeader(request, H_SIGNATURE);
        if (!plugin.getAccessKey().equals(accessKey)) {
            throw new BusinessException(ErrorCode.PLUGIN_ACCESS_DENIED, "插件访问密钥无效");
        }
        long timestampMillis = parseTimestamp(timestamp);
        long now = Instant.now().toEpochMilli();
        long allowed = plugin.getAllowedClockSkewSeconds() * 1000L;
        if (Math.abs(now - timestampMillis) > allowed) {
            throw new BusinessException(ErrorCode.PLUGIN_SIGNATURE_INVALID, "插件请求已过期，请检查本机时间");
        }
        cleanupExpiredNonces(now - allowed);
        Long existing = usedNonces.putIfAbsent(nonce, timestampMillis);
        if (existing != null) {
            throw new BusinessException(ErrorCode.PLUGIN_SIGNATURE_INVALID, "插件请求重复，请重试");
        }
        String canonical = String.join(
                "\n",
                request.getMethod(),
                request.getRequestURI(),
                accessKey,
                timestamp,
                nonce,
                sha256Hex(body == null ? "" : body)
        );
        String expected = hmacSha256Hex(plugin.getAccessSecret(), canonical);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.PLUGIN_SIGNATURE_INVALID, "插件签名校验失败");
        }
    }

    private void cleanupExpiredNonces(long minTimestamp) {
        Iterator<Map.Entry<String, Long>> iterator = usedNonces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() < minTimestamp) {
                iterator.remove();
            }
        }
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PLUGIN_SIGNATURE_INVALID, "缺少插件签名头: " + name);
        }
        return value.trim();
    }

    private long parseTimestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.PLUGIN_SIGNATURE_INVALID, "插件时间戳格式错误");
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "计算插件摘要失败");
        }
    }

    private String hmacSha256Hex(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "计算插件签名失败");
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }
}
