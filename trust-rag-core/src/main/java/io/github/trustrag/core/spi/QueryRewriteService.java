package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagRequest;

import java.util.List;

/**
 * QueryRewriteService 封装一组业务用例，向上层提供清晰的领域操作入口。
 */
public interface QueryRewriteService {

    List<String> rewrite(RagRequest request);
}
