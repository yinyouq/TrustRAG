package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagRequest;

import java.util.List;

public interface QueryRewriteService {

    List<String> rewrite(RagRequest request);
}
