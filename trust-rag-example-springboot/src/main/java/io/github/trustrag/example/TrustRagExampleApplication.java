package io.github.trustrag.example;

import io.github.trustrag.core.model.KnowledgeImportRequest;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.service.KnowledgeIngestionService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * TrustRagExampleApplication 承载 trust-rag-example-springboot 模块中的领域逻辑或基础设施适配职责。
 */
@SpringBootApplication
public class TrustRagExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrustRagExampleApplication.class, args);
    }

    @Bean
    ApplicationRunner sampleKnowledge(KnowledgeIngestionService ingestionService) {
        return args -> ingestionService.importKnowledge(new KnowledgeImportRequest(
                "TrustRAG Engine",
                "TrustRAG Engine 接管查询改写、权限过滤、可信检索、Prompt 构造、模型生成、Trace 记录和知识缺口检测。"
                        + "用户纠错只会进入低可信候选池，必须经过人工审核后才能成为高可信知识。",
                "example",
                "example://bootstrap",
                TrustLevel.HIGH,
                ScopeType.GLOBAL,
                null,
                null,
                null,
                null));
    }
}
