package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.ConflictRecord;

import java.util.List;

public interface ConflictRecordRepository {

    ConflictRecord save(ConflictRecord record);

    List<ConflictRecord> findByCandidateKnowledgeId(long knowledgeId);

    List<ConflictRecord> find(int limit, int offset);
}
