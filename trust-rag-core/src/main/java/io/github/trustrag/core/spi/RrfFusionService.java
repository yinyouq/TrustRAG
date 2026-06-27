package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.HybridCandidate;
import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.VectorHit;

import java.util.List;

/**
 * RrfFusionService 封装一组业务用例，向上层提供清晰的领域操作入口。
 */
public interface RrfFusionService {

    List<HybridCandidate> fuse(
            List<VectorHit> vectorHits,
            List<KeywordHit> keywordHits,
            int rrfK);
}
