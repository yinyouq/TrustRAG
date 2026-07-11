package io.github.trustrag.opensearch;

import io.github.trustrag.core.model.KeywordSearchRequest;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.TrustLevel;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 OpenSearchDefinition 的关键行为、边界条件和回归场景。
 */
class OpenSearchDefinitionTest {

    @Test
    void definesWeightedBm25FieldsAndStrictMapping() {
        var request = new OpenSearchIndexDefinition()
                .createRequest("trust_rag_knowledge_keyword");

        assertThat(request.index()).isEqualTo("trust_rag_knowledge_keyword");
        assertThat(request.mappings().properties())
                .containsKeys(
                        "knowledge_id", "title", "claim", "content", "tags",
                        "trust_level", "scope_type", "status");
        assertThat(request.mappings().properties().get("title").text().analyzer())
                .isEqualTo("trust_rag_text_analyzer");
        assertThat(request.mappings().properties().get("tags").isKeyword()).isTrue();
        assertThat(request.settings().numberOfShards()).isEqualTo("1");
        assertThat(request.settings().numberOfReplicas()).isEqualTo("0");
        assertThat(request.settings().analysis().analyzer())
                .containsKey("trust_rag_text_analyzer");
    }

    @Test
    void buildsServerSideStatusTrustAndScopeFilters() {
        var query = new OpenSearchQueryFactory().create(new KeywordSearchRequest(
                "Spring AI tool callback",
                new ScopeContext("user-1", "conversation-1", null, "tenant-1"),
                Set.of(TrustLevel.HIGH, TrustLevel.MEDIUM, TrustLevel.LOW),
                Set.of(
                        KnowledgeStatus.HIGH_ENABLED,
                        KnowledgeStatus.MEDIUM_ENABLED,
                        KnowledgeStatus.LOW_ENABLED),
                Set.of(),
                30,
                false));

        assertThat(query.isBool()).isTrue();
        assertThat(query.bool().must()).singleElement()
                .satisfies(text -> assertThat(text.multiMatch().fields())
                        .containsExactly("title^3", "claim^2", "tags^2", "content"));
        assertThat(query.bool().filter()).hasSize(3);
        assertThat(query.bool().filter().get(2).bool().should())
                .hasSize(4);
    }

    @Test
    void trimsLongKeywordQueryBeforeBuildingBm25MultiMatch() {
        String longQuery = IntStream.rangeClosed(1, 1_500)
                .mapToObj(index -> "term" + index)
                .collect(Collectors.joining(" "));

        var query = new OpenSearchQueryFactory().create(new KeywordSearchRequest(
                longQuery,
                new ScopeContext("user-1", "conversation-1", null, "tenant-1"),
                Set.of(TrustLevel.HIGH, TrustLevel.MEDIUM, TrustLevel.LOW),
                Set.of(
                        KnowledgeStatus.HIGH_ENABLED,
                        KnowledgeStatus.MEDIUM_ENABLED,
                        KnowledgeStatus.LOW_ENABLED),
                Set.of(),
                30,
                false));

        String sanitized = query.bool().must().get(0).multiMatch().query();

        assertThat(sanitized.split("\\s+")).hasSize(OpenSearchKeywordQuerySanitizer.MAX_QUERY_TERMS);
        assertThat(sanitized).startsWith("term1 term2");
        assertThat(sanitized).endsWith("term128");
    }
}
