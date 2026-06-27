package io.github.trustrag.core.service;

import io.github.trustrag.core.model.RagRequest;
import io.github.trustrag.core.spi.QueryRewriteService;

import java.util.List;

/**
 * DefaultQueryRewriteService 封装一组业务用例，向上层提供清晰的领域操作入口。
 */
public final class DefaultQueryRewriteService implements QueryRewriteService {

    @Override
    public List<String> rewrite(RagRequest request) {
        return List.of(request.question().trim());
    }
}
