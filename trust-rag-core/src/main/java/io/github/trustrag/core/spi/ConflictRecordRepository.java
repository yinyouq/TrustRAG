package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.ConflictRecord;

import java.util.List;

/**
 * ConflictRecordRepository 定义领域服务依赖的持久化接口，具体实现可由不同存储适配。
 */
public interface ConflictRecordRepository {

    ConflictRecord save(ConflictRecord record);

    List<ConflictRecord> findByCandidateKnowledgeId(long knowledgeId);

    List<ConflictRecord> find(int limit, int offset);
}
