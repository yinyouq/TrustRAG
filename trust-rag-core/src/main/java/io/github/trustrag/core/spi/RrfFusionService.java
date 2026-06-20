package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.HybridCandidate;
import io.github.trustrag.core.model.KeywordHit;
import io.github.trustrag.core.model.VectorHit;

import java.util.List;

public interface RrfFusionService {

    List<HybridCandidate> fuse(
            List<VectorHit> vectorHits,
            List<KeywordHit> keywordHits,
            int rrfK);
}
