package top.fusb.huatuo.dx.agent.service;

import top.fusb.huatuo.dx.agent.config.HuatuoAgentProperties;
import top.fusb.huatuo.dx.agent.dto.ArthasStatusView;
import top.fusb.huatuo.dx.agent.exception.BusinessException;
import top.fusb.huatuo.dx.agent.exception.ErrorCode;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ArthasInstallerService {

    private final HuatuoAgentProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ArthasInstallerService(HuatuoAgentProperties properties) {
        this.properties = properties;
    }

    public ArthasStatusView status() {
        Path configured = configuredArthasPath().orElse(Path.of(properties.getArthasInstallDir())
                .toAbsolutePath()
                .normalize()
                .resolve("arthas-boot-" + properties.getArthasVersion() + ".jar"));
        boolean installed = Files.exists(configured);
        return new ArthasStatusView(
                installed,
                installed ? "READY" : "MISSING",
                configured.toString(),
                installed ? "Arthas 已就绪" : "Arthas 未安装"
        );
    }

    public ArthasStatusView install() {
        Path jar = resolveArthasBootJar();
        return new ArthasStatusView(true, "READY", jar.toString(), "Arthas 安装完成");
    }

    public Path resolveArthasBootJar() {
        Path configured = configuredArthasPath().orElse(null);
        if (configured != null && Files.exists(configured)) {
            return configured;
        }
        try {
            Path installDir = Path.of(properties.getArthasInstallDir()).toAbsolutePath().normalize();
            Files.createDirectories(installDir);
            Path target = installDir.resolve("arthas-boot-" + properties.getArthasVersion() + ".jar");
            if (Files.exists(target)) {
                return target;
            }
            download(target);
            properties.setArthasBootJar(target.toString());
            return target;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.ARTHAS_INSTALL_FAILED, "自动安装 Arthas 失败: " + e.getMessage());
        }
    }

    private Optional<Path> configuredArthasPath() {
        String raw = properties.getArthasBootJar();
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(raw).toAbsolutePath().normalize());
    }

    private void download(Path target) throws Exception {
        String version = properties.getArthasVersion();
        String baseUrl = properties.getArthasDownloadBaseUrl();
        String fileName = "arthas-boot-" + version + ".jar";
        List<String> candidates = List.of(
                baseUrl,
                "https://repo1.maven.org/maven2/com/taobao/arthas/arthas-boot/" + version + "/" + fileName,
                "https://repo.maven.apache.org/maven2/com/taobao/arthas/arthas-boot/" + version + "/" + fileName
        );

        HttpResponse<InputStream> response = null;
        String lastError = null;
        for (String candidate : candidates) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(candidate))
                    .timeout(Duration.ofMinutes(2))
                    .GET()
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                break;
            }
            lastError = candidate + " -> HTTP " + response.statusCode();
            response.body().close();
            response = null;
        }
        if (response == null) {
            throw new IllegalStateException("Failed to download Arthas boot jar: " + lastError);
        }
        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(temp)) {
            input.transferTo(output);
        }
        Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
