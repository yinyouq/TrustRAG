package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.KnowledgeItem;

import java.util.List;

/**
 * KnowledgeIndexService 封装一组业务用例，向上层提供清晰的领域操作入口。
 */
public interface KnowledgeIndexService {

    void upsert(KnowledgeItem knowledge, List<Float> vector);

    void delete(long knowledgeId);
}
