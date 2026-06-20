package io.github.trustrag.evaluation;

import java.util.List;

public interface EvalTraceReader {

    List<Long> findRetrievedKnowledgeIds(String traceId);

    List<Long> findUsedKnowledgeIds(String traceId);
}
