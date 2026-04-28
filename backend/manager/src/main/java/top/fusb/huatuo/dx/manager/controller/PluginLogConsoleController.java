package top.fusb.huatuo.dx.manager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.fusb.huatuo.dx.manager.dto.LogConsoleCatalogResponse;
import top.fusb.huatuo.dx.manager.dto.LogConsoleContextResponse;
import top.fusb.huatuo.dx.manager.dto.LogConsoleQueryResponse;
import top.fusb.huatuo.dx.manager.dto.LogTailResponse;
import top.fusb.huatuo.dx.manager.dto.PluginLogConsoleContextRequest;
import top.fusb.huatuo.dx.manager.dto.PluginLogConsoleQueryRequest;
import top.fusb.huatuo.dx.manager.exception.BusinessException;
import top.fusb.huatuo.dx.manager.exception.ErrorCode;
import top.fusb.huatuo.dx.manager.service.LogConsoleService;
import top.fusb.huatuo.dx.manager.service.PluginSignatureService;

@RestController
@RequestMapping("/api/plugin/log-console")
public class PluginLogConsoleController {

    private final PluginSignatureService pluginSignatureService;
    private final LogConsoleService logConsoleService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public PluginLogConsoleController(
            PluginSignatureService pluginSignatureService,
            LogConsoleService logConsoleService,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.pluginSignatureService = pluginSignatureService;
        this.logConsoleService = logConsoleService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @GetMapping("/catalog")
    public LogConsoleCatalogResponse catalog(HttpServletRequest request) {
        pluginSignatureService.verify(request, "");
        return logConsoleService.getCatalog();
    }

    @PostMapping("/query")
    public LogConsoleQueryResponse query(@RequestBody String rawBody, HttpServletRequest request) throws Exception {
        pluginSignatureService.verify(request, rawBody);
        PluginLogConsoleQueryRequest queryRequest = objectMapper.readValue(rawBody, PluginLogConsoleQueryRequest.class);
        validate(queryRequest);
        return logConsoleService.query(new top.fusb.huatuo.dx.manager.dto.LogConsoleQueryRequest(
                queryRequest.nodeId(),
                queryRequest.sourceId(),
                queryRequest.keyword() == null ? "" : queryRequest.keyword(),
                queryRequest.page() == null ? 1 : queryRequest.page(),
                queryRequest.pageSize() == null ? 50 : queryRequest.pageSize(),
                queryRequest.tailMode()
        ));
    }

    @PostMapping("/context")
    public LogConsoleContextResponse context(@RequestBody String rawBody, HttpServletRequest request) throws Exception {
        pluginSignatureService.verify(request, rawBody);
        PluginLogConsoleContextRequest contextRequest = objectMapper.readValue(rawBody, PluginLogConsoleContextRequest.class);
        validate(contextRequest);
        return logConsoleService.context(new top.fusb.huatuo.dx.manager.dto.LogConsoleContextRequest(
                contextRequest.nodeId(),
                contextRequest.sourceId(),
                contextRequest.filePath(),
                contextRequest.lineNumber(),
                contextRequest.beforeLines() == null ? 10 : contextRequest.beforeLines(),
                contextRequest.afterLines() == null ? 10 : contextRequest.afterLines()
        ));
    }

    @GetMapping(path = "/tail/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter tailStream(
            HttpServletRequest request,
            @RequestParam("nodeId") Long nodeId,
            @RequestParam("sourceId") String sourceId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "afterCollectedAtEpochMs", required = false) Long afterCollectedAtEpochMs,
            @RequestParam(value = "afterFilePath", required = false) String afterFilePath,
            @RequestParam(value = "afterLineNumber", required = false) Integer afterLineNumber
    ) {
        pluginSignatureService.verify(request, "");
        if (nodeId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "节点不能为空");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日志源不能为空");
        }
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        CompletableFuture.runAsync(() -> streamTail(
                emitter,
                nodeId,
                sourceId,
                keyword == null ? "" : keyword,
                afterCollectedAtEpochMs,
                afterFilePath,
                afterLineNumber
        ));
        return emitter;
    }

    private void streamTail(
            SseEmitter emitter,
            Long nodeId,
            String sourceId,
            String keyword,
            Long afterCollectedAtEpochMs,
            String afterFilePath,
            Integer afterLineNumber
    ) {
        Long watermarkTime = afterCollectedAtEpochMs;
        String watermarkFile = afterFilePath;
        Integer watermarkLine = afterLineNumber;
        try {
            while (true) {
                LogTailResponse response = logConsoleService.tail(
                        nodeId,
                        sourceId,
                        keyword,
                        watermarkTime,
                        watermarkFile,
                        watermarkLine,
                        200
                );
                if (!response.items().isEmpty()) {
                    watermarkTime = response.latestCollectedAtEpochMs();
                    watermarkFile = response.latestFilePath();
                    watermarkLine = response.latestLineNumber();
                    emitter.send(SseEmitter.event().name("append").data(response));
                } else {
                    emitter.send(SseEmitter.event().name("ping").data("ok"));
                }
                Thread.sleep(1000L);
            }
        } catch (IOException exception) {
            emitter.complete();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (Exception exception) {
            try {
                emitter.send(SseEmitter.event().name("error").data(exception.getMessage() == null ? "日志流异常" : exception.getMessage()));
            } catch (IOException ignored) {
                // ignore client disconnect while sending error
            }
            emitter.complete();
        }
    }

    private <T> void validate(T request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求不能为空");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("; "));
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }
}
