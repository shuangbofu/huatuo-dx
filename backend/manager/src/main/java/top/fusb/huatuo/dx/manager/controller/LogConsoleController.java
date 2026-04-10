package top.fusb.huatuo.dx.manager.controller;

import top.fusb.huatuo.dx.manager.dto.LogConsoleCatalogResponse;
import top.fusb.huatuo.dx.manager.dto.LogConsoleContextRequest;
import top.fusb.huatuo.dx.manager.dto.LogConsoleContextResponse;
import top.fusb.huatuo.dx.manager.dto.LogConsoleQueryRequest;
import top.fusb.huatuo.dx.manager.dto.LogConsoleQueryResponse;
import top.fusb.huatuo.dx.manager.dto.LogTailResponse;
import top.fusb.huatuo.dx.manager.service.LogConsoleService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/log-console")
public class LogConsoleController {

    private final LogConsoleService logConsoleService;

    public LogConsoleController(LogConsoleService logConsoleService) {
        this.logConsoleService = logConsoleService;
    }

    @GetMapping("/catalog")
    public LogConsoleCatalogResponse catalog() {
        return logConsoleService.getCatalog();
    }

    @PostMapping("/query")
    public LogConsoleQueryResponse query(@Valid @RequestBody LogConsoleQueryRequest request) {
        return logConsoleService.query(request);
    }

    @PostMapping("/context")
    public LogConsoleContextResponse context(@Valid @RequestBody LogConsoleContextRequest request) {
        return logConsoleService.context(request);
    }

    @GetMapping(path = "/tail/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter tailStream(
            @RequestParam("nodeId") Long nodeId,
            @RequestParam("sourceId") String sourceId,
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "afterCollectedAtEpochMs", required = false) Long afterCollectedAtEpochMs,
            @RequestParam(value = "afterFilePath", required = false) String afterFilePath,
            @RequestParam(value = "afterLineNumber", required = false) Integer afterLineNumber
    ) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        CompletableFuture.runAsync(() -> streamTail(emitter, nodeId, sourceId, keyword, afterCollectedAtEpochMs, afterFilePath, afterLineNumber));
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
}
