package io.github.trustrag.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 检索基础设施内存 API。
 */
@RestController
@RequestMapping("/trust-rag/admin/infrastructure")
public final class InfrastructureMemoryController {

    private final InfrastructureMemoryService service;

    InfrastructureMemoryController(InfrastructureMemoryService service) {
        this.service = service;
    }

    @GetMapping("/memory")
    public InfrastructureMemorySnapshot memory(
            @RequestParam(required = false) Integer windowMinutes) {
        return service.snapshot(windowMinutes);
    }
}
