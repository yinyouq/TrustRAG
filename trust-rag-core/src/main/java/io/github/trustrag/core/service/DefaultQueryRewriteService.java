package io.github.trustrag.core.service;

import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.spi.QueryRewriteService;

import java.util.List;

public final class DefaultQueryRewriteService implements QueryRewriteService {

    @Override
    public List<String> rewrite(RagRequest request) {
        return List.of(request.question().trim());
    }
}
