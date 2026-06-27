package io.github.trustrag.evaluation;

import java.util.List;

/**
 * EvalTraceReader 负责从已保存的 RAG 轨迹中读取评估需要的检索信息。
 */
public interface EvalTraceReader {

    List<Long> findRetrievedKnowledgeIds(String traceId);

    List<Long> findUsedKnowledgeIds(String traceId);
}
