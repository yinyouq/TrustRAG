package io.github.trustrag.core.service;

import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.VectorHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 DefaultRrfFusionService 的关键行为、边界条件和回归场景。
 */
class DefaultRrfFusionServiceTest {

    @Test
    void combinesRanksFromBothRetrievers() {
        var result = new DefaultRrfFusionService().fuse(
                List.of(new VectorHit(1L, 0.91), new VectorHit(2L, 0.88)),
                List.of(new KeywordHit(2L, 12.0), new KeywordHit(3L, 8.0)),
                60);

        assertThat(result).extracting(value -> value.knowledgeId())
                .containsExactly(2L, 1L, 3L);
        assertThat(result.get(0).rrfScore())
                .isEqualTo(1.0 / 62.0 + 1.0 / 61.0);
        assertThat(result.get(0).vectorRank()).isEqualTo(2);
        assertThat(result.get(0).keywordRank()).isEqualTo(1);
    }
}
