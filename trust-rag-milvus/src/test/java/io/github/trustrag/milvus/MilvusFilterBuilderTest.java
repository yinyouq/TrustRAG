package io.github.trustrag.milvus;

import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorSearchRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MilvusFilterBuilderTest {

    private final MilvusFilterBuilder builder = new MilvusFilterBuilder();

    @Test
    void lowTrustSearchNeverIncludesGlobalScope() {
        MilvusFilter filter = builder.build(new VectorSearchRequest(
                List.of(0.1f),
                new ScopeContext("user-1", null, null, "tenant-1"),
                Set.of(TrustLevel.LOW),
                Set.of(KnowledgeStatus.LOW_ENABLED),
                3,
                0.5));

        assertThat(filter.expression()).doesNotContain("scope_type == \"GLOBAL\"");
        assertThat(filter.expression()).contains("user_id == {userId}", "tenant_id == {tenantId}");
        assertThat(filter.templateValues()).containsEntry("userId", "user-1");
    }

    @Test
    void highTrustSearchIncludesGlobalAndOwnedScopes() {
        MilvusFilter filter = builder.build(new VectorSearchRequest(
                List.of(0.1f),
                new ScopeContext(null, null, "project-1", null),
                Set.of(TrustLevel.HIGH),
                Set.of(KnowledgeStatus.HIGH_ENABLED),
                5,
                0.5));

        assertThat(filter.expression()).contains("scope_type == \"GLOBAL\"", "project_id == {projectId}");
        assertThat(filter.expression()).contains("trust_level in {trustedGlobalLevels}");
        assertThat(filter.templateValues())
                .containsEntry("trustedGlobalLevels", List.of("HIGH"));
    }
}
