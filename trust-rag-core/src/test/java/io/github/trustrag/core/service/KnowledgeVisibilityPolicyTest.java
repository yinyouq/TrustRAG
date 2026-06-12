package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeVisibilityPolicyTest {

    private final KnowledgeVisibilityPolicy policy = new KnowledgeVisibilityPolicy();

    @Test
    void privateKnowledgeIsVisibleOnlyToItsOwner() {
        KnowledgeItem item = item(TrustLevel.LOW, ScopeType.USER, "user-a");

        assertThat(policy.isVisible(item, new ScopeContext("user-a", null, null, null))).isTrue();
        assertThat(policy.isVisible(item, new ScopeContext("user-b", null, null, null))).isFalse();
        assertThat(policy.isVisible(item, new ScopeContext(null, null, null, null))).isFalse();
    }

    @Test
    void lowTrustGlobalKnowledgeIsNeverVisible() {
        KnowledgeItem item = item(TrustLevel.LOW, ScopeType.GLOBAL, null);

        assertThat(policy.isVisible(item, new ScopeContext("any", "any", "any", "any"))).isFalse();
    }

    private KnowledgeItem item(TrustLevel trustLevel, ScopeType scopeType, String userId) {
        Instant now = Instant.parse("2026-06-12T00:00:00Z");
        return new KnowledgeItem(
                1L, "title", null, "content", null, "test",
                trustLevel, KnowledgeStatus.ENABLED, scopeType,
                userId, null, null, null,
                "test", null, null, "1", "test", 3,
                1.0, 0.0, 1, "hash", null, null, null, now, now, null);
    }
}
