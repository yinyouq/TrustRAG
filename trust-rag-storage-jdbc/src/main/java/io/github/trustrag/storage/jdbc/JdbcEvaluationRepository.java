package io.github.trustrag.storage.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.evaluation.BeforeAfterGroup;
import io.github.trustrag.evaluation.EvalCase;
import io.github.trustrag.evaluation.EvalCompareReport;
import io.github.trustrag.evaluation.EvalDataset;
import io.github.trustrag.evaluation.EvalGovernanceSnapshot;
import io.github.trustrag.evaluation.EvalJudgeDetail;
import io.github.trustrag.evaluation.EvalReport;
import io.github.trustrag.evaluation.EvalResult;
import io.github.trustrag.evaluation.EvalResultStatus;
import io.github.trustrag.evaluation.EvalRun;
import io.github.trustrag.evaluation.EvalRunStatus;
import io.github.trustrag.evaluation.EvalRunType;
import io.github.trustrag.evaluation.EvaluationRepository;
import io.github.trustrag.evaluation.ExpectedKnowledge;
import io.github.trustrag.evaluation.JudgeType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 评估模块的 JDBC 仓储实现。
 *
 * <p>同一个仓储聚合测试集、用例、运行、结果、Judge 明细、报告和治理快照，
 * 便于评估 API 在一个事务边界内维护关联数据。</p>
 */
public class JdbcEvaluationRepository implements EvaluationRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcEvaluationRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvalDataset saveDataset(EvalDataset dataset) {
        if (dataset.id() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO eval_dataset (
                        name, description, tenant_id, project_id, created_by,
                        enabled, created_at, updated_at
                    ) VALUES (
                        :name, :description, :tenantId, :projectId, :createdBy,
                        :enabled, :createdAt, :updatedAt
                    )
                    """, datasetParameters(dataset), keys, new String[]{"id"});
            return dataset.withId(key(keys, "eval dataset"));
        }
        jdbc.update("""
                UPDATE eval_dataset SET
                    name=:name, description=:description, tenant_id=:tenantId,
                    project_id=:projectId, enabled=:enabled, updated_at=:updatedAt
                WHERE id=:id
                """, datasetParameters(dataset).addValue("id", dataset.id()));
        return dataset;
    }

    @Override
    public Optional<EvalDataset> findDataset(long datasetId) {
        return queryOne(
                "SELECT * FROM eval_dataset WHERE id=:id",
                Map.of("id", datasetId),
                this::mapDataset);
    }

    @Override
    public List<EvalDataset> listDatasets(
            String tenantId,
            String projectId,
            boolean includeDisabled,
            int limit,
            int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM eval_dataset WHERE 1=1");
        MapSqlParameterSource parameters = limitOffset(limit, offset);
        appendScope(sql, parameters, tenantId, projectId);
        if (!includeDisabled) {
            sql.append(" AND enabled=TRUE");
        }
        sql.append(" ORDER BY created_at DESC LIMIT :limit OFFSET :offset");
        return jdbc.query(sql.toString(), parameters, this::mapDataset);
    }

    @Override
    public boolean datasetHasRuns(long datasetId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM eval_run WHERE dataset_id=:datasetId",
                Map.of("datasetId", datasetId),
                Long.class);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void deleteDataset(long datasetId) {
        jdbc.update("""
                DELETE FROM eval_case_expected_knowledge
                WHERE eval_case_id IN (
                    SELECT id FROM eval_case WHERE dataset_id=:datasetId
                )
                """, Map.of("datasetId", datasetId));
        jdbc.update(
                "DELETE FROM eval_case WHERE dataset_id=:datasetId",
                Map.of("datasetId", datasetId));
        jdbc.update(
                "DELETE FROM eval_dataset WHERE id=:datasetId",
                Map.of("datasetId", datasetId));
    }

    @Override
    public EvalCase saveCase(EvalCase evalCase) {
        if (evalCase.id() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO eval_case (
                        dataset_id, question, expected_answer, tenant_id, project_id,
                        user_id, conversation_id, tags, difficulty, enabled,
                        created_at, updated_at
                    ) VALUES (
                        :datasetId, :question, :expectedAnswer, :tenantId, :projectId,
                        :userId, :conversationId, :tags, :difficulty, :enabled,
                        :createdAt, :updatedAt
                    )
                    """, caseParameters(evalCase), keys, new String[]{"id"});
            return evalCase.withId(key(keys, "eval case"));
        }
        jdbc.update("""
                UPDATE eval_case SET
                    question=:question, expected_answer=:expectedAnswer,
                    tenant_id=:tenantId, project_id=:projectId, user_id=:userId,
                    conversation_id=:conversationId, tags=:tags,
                    difficulty=:difficulty, enabled=:enabled, updated_at=:updatedAt
                WHERE id=:id
                """, caseParameters(evalCase).addValue("id", evalCase.id()));
        return evalCase;
    }

    @Override
    @Transactional
    public EvalCase saveCaseWithExpectedKnowledge(
            EvalCase evalCase, List<ExpectedKnowledge> expectedKnowledge) {
        // 用例和期望知识一起保存，保证评估运行看到的是完整可用的测试样本。
        EvalCase saved = saveCase(evalCase);
        replaceExpectedKnowledge(saved.id(), expectedKnowledge);
        return saved;
    }

    @Override
    public Optional<EvalCase> findCase(long evalCaseId) {
        return queryOne(
                "SELECT * FROM eval_case WHERE id=:id",
                Map.of("id", evalCaseId),
                this::mapCase);
    }

    @Override
    public List<EvalCase> listCases(long datasetId, boolean onlyEnabled, int limit, int offset) {
        String sql = """
                SELECT * FROM eval_case
                WHERE dataset_id=:datasetId
                """ + (onlyEnabled ? " AND enabled=TRUE" : "")
                + " ORDER BY id ASC LIMIT :limit OFFSET :offset";
        return jdbc.query(sql,
                limitOffset(limit, offset).addValue("datasetId", datasetId),
                this::mapCase);
    }

    @Override
    public boolean caseHasResults(long evalCaseId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM eval_result WHERE eval_case_id=:evalCaseId",
                Map.of("evalCaseId", evalCaseId),
                Long.class);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void deleteCase(long evalCaseId) {
        jdbc.update(
                "DELETE FROM eval_case_expected_knowledge WHERE eval_case_id=:evalCaseId",
                Map.of("evalCaseId", evalCaseId));
        jdbc.update(
                "DELETE FROM eval_case WHERE id=:evalCaseId",
                Map.of("evalCaseId", evalCaseId));
    }

    @Override
    @Transactional
    public void replaceExpectedKnowledge(long evalCaseId, List<ExpectedKnowledge> expectedKnowledge) {
        jdbc.update(
                "DELETE FROM eval_case_expected_knowledge WHERE eval_case_id=:evalCaseId",
                Map.of("evalCaseId", evalCaseId));
        if (expectedKnowledge == null || expectedKnowledge.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = new MapSqlParameterSource[expectedKnowledge.size()];
        Instant defaultCreatedAt = Instant.now();
        for (int index = 0; index < expectedKnowledge.size(); index++) {
            ExpectedKnowledge item = expectedKnowledge.get(index);
            Instant createdAt = item.createdAt() == null ? defaultCreatedAt : item.createdAt();
            batch[index] = new MapSqlParameterSource()
                    .addValue("evalCaseId", evalCaseId)
                    .addValue("knowledgeId", item.knowledgeId())
                    .addValue("relevanceGrade", item.relevanceGrade())
                    .addValue("createdAt", timestamp(createdAt));
        }
        jdbc.batchUpdate("""
                INSERT INTO eval_case_expected_knowledge (
                    eval_case_id, knowledge_id, relevance_grade, created_at
                ) VALUES (
                    :evalCaseId, :knowledgeId, :relevanceGrade, :createdAt
                )
                """, batch);
    }

    @Override
    public List<ExpectedKnowledge> listExpectedKnowledge(long evalCaseId) {
        return jdbc.query("""
                        SELECT * FROM eval_case_expected_knowledge
                        WHERE eval_case_id=:evalCaseId
                        ORDER BY relevance_grade DESC, id ASC
                        """,
                Map.of("evalCaseId", evalCaseId),
                this::mapExpectedKnowledge);
    }

    @Override
    public EvalRun createRun(EvalRun run) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO eval_run (
                    dataset_id, run_name, run_type, before_after_group, status,
                    total_count, success_count, failed_count, engine_config_snapshot,
                    model_config_snapshot, knowledge_snapshot_time, started_at,
                    finished_at, error_message, created_by, created_at
                ) VALUES (
                    :datasetId, :runName, :runType, :beforeAfterGroup, :status,
                    :totalCount, :successCount, :failedCount, :engineConfigSnapshot,
                    :modelConfigSnapshot, :knowledgeSnapshotTime, :startedAt,
                    :finishedAt, :errorMessage, :createdBy, :createdAt
                )
                """, runParameters(run), keys, new String[]{"id"});
        return run.withId(key(keys, "eval run"));
    }

    @Override
    public Optional<EvalRun> findRun(long runId) {
        return queryOne(
                "SELECT * FROM eval_run WHERE id=:id",
                Map.of("id", runId),
                this::mapRun);
    }

    @Override
    public List<EvalRun> listRuns(Long datasetId, int limit, int offset) {
        String sql = "SELECT * FROM eval_run"
                + (datasetId == null ? "" : " WHERE dataset_id=:datasetId")
                + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset";
        MapSqlParameterSource parameters = limitOffset(limit, offset);
        if (datasetId != null) {
            parameters.addValue("datasetId", datasetId);
        }
        return jdbc.query(sql, parameters, this::mapRun);
    }

    @Override
    public void markRunRunning(long runId, Instant startedAt) {
        jdbc.update("""
                UPDATE eval_run
                SET status='RUNNING', started_at=:startedAt, error_message=NULL
                WHERE id=:id AND status IN ('PENDING', 'RUNNING')
                """,
                new MapSqlParameterSource()
                        .addValue("id", runId)
                        .addValue("startedAt", timestamp(startedAt)));
    }

    @Override
    public void updateRunProgress(long runId, int successCount, int failedCount) {
        jdbc.update("""
                UPDATE eval_run
                SET success_count=:successCount, failed_count=:failedCount
                WHERE id=:id
                """,
                new MapSqlParameterSource()
                        .addValue("id", runId)
                        .addValue("successCount", successCount)
                        .addValue("failedCount", failedCount));
    }

    @Override
    public void completeRun(
            long runId,
            EvalRunStatus status,
            int successCount,
            int failedCount,
            Instant finishedAt,
            String errorMessage) {
        jdbc.update("""
                UPDATE eval_run
                SET status=:status, success_count=:successCount, failed_count=:failedCount,
                    finished_at=:finishedAt, error_message=:errorMessage
                WHERE id=:id
                """,
                new MapSqlParameterSource()
                        .addValue("id", runId)
                        .addValue("status", status.name())
                        .addValue("successCount", successCount)
                        .addValue("failedCount", failedCount)
                        .addValue("finishedAt", timestamp(finishedAt))
                        .addValue("errorMessage", errorMessage));
    }

    @Override
    public EvalResult saveResult(EvalResult result) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO eval_result (
                    eval_run_id, eval_case_id, trace_id, question, expected_answer,
                    answer, retrieved_count, expected_knowledge_count, recall_at_5,
                    recall_at_10, precision_at_5, precision_at_10, mrr,
                    ndcg_at_5, ndcg_at_10, faithfulness, answer_correctness,
                    answer_relevance, hallucination_score, prompt_tokens,
                    completion_tokens, latency_ms, status, error_message, created_at
                ) VALUES (
                    :evalRunId, :evalCaseId, :traceId, :question, :expectedAnswer,
                    :answer, :retrievedCount, :expectedKnowledgeCount, :recallAt5,
                    :recallAt10, :precisionAt5, :precisionAt10, :mrr,
                    :ndcgAt5, :ndcgAt10, :faithfulness, :answerCorrectness,
                    :answerRelevance, :hallucinationScore, :promptTokens,
                    :completionTokens, :latencyMs, :status, :errorMessage, :createdAt
                )
                """, resultParameters(result), keys, new String[]{"id"});
        return result.withId(key(keys, "eval result"));
    }

    @Override
    public List<EvalResult> listResults(long runId, int limit, int offset) {
        return jdbc.query("""
                        SELECT * FROM eval_result
                        WHERE eval_run_id=:runId
                        ORDER BY id ASC LIMIT :limit OFFSET :offset
                        """,
                limitOffset(limit, offset).addValue("runId", runId),
                this::mapResult);
    }

    @Override
    public EvalJudgeDetail saveJudgeDetail(EvalJudgeDetail detail) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO eval_judge_detail (
                    eval_result_id, eval_run_id, eval_case_id, judge_type, model,
                    prompt, raw_output, score, passed, reason, created_at
                ) VALUES (
                    :evalResultId, :evalRunId, :evalCaseId, :judgeType, :model,
                    :prompt, :rawOutput, :score, :passed, :reason, :createdAt
                )
                """, judgeParameters(detail), keys, new String[]{"id"});
        return detail.withId(key(keys, "eval judge detail"));
    }

    @Override
    public List<EvalJudgeDetail> listJudgeDetails(long resultId) {
        return jdbc.query("""
                        SELECT * FROM eval_judge_detail
                        WHERE eval_result_id=:resultId
                        ORDER BY id ASC
                        """,
                Map.of("resultId", resultId),
                this::mapJudgeDetail);
    }

    @Override
    @Transactional
    public EvalReport saveReport(EvalReport report) {
        // 报告按 run 幂等重建，重复生成时覆盖旧汇总而不是追加多份报告。
        jdbc.update("DELETE FROM eval_report WHERE eval_run_id=:runId", Map.of("runId", report.evalRunId()));
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO eval_report (
                    eval_run_id, dataset_id, total_count, success_count, failed_count,
                    avg_recall_at_5, avg_recall_at_10, avg_precision_at_5,
                    avg_precision_at_10, avg_mrr, avg_ndcg_at_5, avg_ndcg_at_10,
                    avg_faithfulness, avg_answer_correctness, avg_answer_relevance,
                    avg_hallucination_score, avg_latency_ms, p90_latency_ms,
                    summary_json, created_at
                ) VALUES (
                    :evalRunId, :datasetId, :totalCount, :successCount, :failedCount,
                    :avgRecallAt5, :avgRecallAt10, :avgPrecisionAt5,
                    :avgPrecisionAt10, :avgMrr, :avgNdcgAt5, :avgNdcgAt10,
                    :avgFaithfulness, :avgAnswerCorrectness, :avgAnswerRelevance,
                    :avgHallucinationScore, :avgLatencyMs, :p90LatencyMs,
                    :summaryJson, :createdAt
                )
                """, reportParameters(report), keys, new String[]{"id"});
        return report.withId(key(keys, "eval report"));
    }

    @Override
    public Optional<EvalReport> findReportByRunId(long runId) {
        return queryOne(
                "SELECT * FROM eval_report WHERE eval_run_id=:runId",
                Map.of("runId", runId),
                this::mapReport);
    }

    @Override
    public EvalCompareReport saveCompareReport(EvalCompareReport report) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO eval_compare_report (
                    before_run_id, after_run_id, recall_at_10_delta, mrr_delta,
                    faithfulness_delta, answer_correctness_delta,
                    hallucination_score_delta, conclusion, created_at
                ) VALUES (
                    :beforeRunId, :afterRunId, :recallAt10Delta, :mrrDelta,
                    :faithfulnessDelta, :answerCorrectnessDelta,
                    :hallucinationScoreDelta, :conclusion, :createdAt
                )
                """, compareParameters(report), keys, new String[]{"id"});
        return report.withId(key(keys, "eval compare report"));
    }

    @Override
    public EvalGovernanceSnapshot captureGovernanceSnapshot(
            String tenantId,
            String projectId,
            LocalDate snapshotDate,
            Instant createdAt) {
        long totalCandidates = count("""
                SELECT COUNT(*) FROM review_task rt
                JOIN knowledge_item ki ON ki.id=rt.knowledge_id
                WHERE 1=1
                """, tenantId, projectId, "ki");
        long approvedCandidates = count("""
                SELECT COUNT(*) FROM review_task rt
                JOIN knowledge_item ki ON ki.id=rt.knowledge_id
                WHERE rt.status='APPROVED'
                """, tenantId, projectId, "ki");
        long rejectedCandidates = count("""
                SELECT COUNT(*) FROM review_task rt
                JOIN knowledge_item ki ON ki.id=rt.knowledge_id
                WHERE rt.status='REJECTED'
                """, tenantId, projectId, "ki");
        long totalKnowledge = count(
                "SELECT COUNT(*) FROM knowledge_item ki WHERE 1=1",
                tenantId,
                projectId,
                "ki");
        long reusedKnowledge = count("""
                SELECT COUNT(DISTINCT rl.knowledge_id)
                FROM retrieval_log rl
                JOIN rag_trace rt ON rt.trace_id=rl.trace_id
                JOIN knowledge_item ki ON ki.id=rl.knowledge_id
                WHERE rl.used_in_prompt=TRUE AND rt.trace_type='NORMAL'
                """, tenantId, projectId, "ki");
        long usedLowTrust = count("""
                SELECT COUNT(*) FROM retrieval_log rl
                JOIN rag_trace rt ON rt.trace_id=rl.trace_id
                JOIN knowledge_item ki ON ki.id=rl.knowledge_id
                WHERE rl.used_in_prompt=TRUE AND rt.trace_type='NORMAL'
                  AND ki.trust_level='LOW'
                """, tenantId, projectId, "ki");
        long usedTotal = count("""
                SELECT COUNT(*) FROM retrieval_log rl
                JOIN rag_trace rt ON rt.trace_id=rl.trace_id
                JOIN knowledge_item ki ON ki.id=rl.knowledge_id
                WHERE rl.used_in_prompt=TRUE AND rt.trace_type='NORMAL'
                """, tenantId, projectId, "ki");
        long privacyEvents = count("""
                SELECT COUNT(*) FROM privacy_event pe
                LEFT JOIN rag_trace rt ON rt.trace_id=pe.trace_id
                LEFT JOIN knowledge_item ki ON ki.id=pe.knowledge_id
                WHERE 1=1
                """, tenantId, projectId, "pe");
        long normalTraces = count(
                "SELECT COUNT(*) FROM rag_trace rt WHERE rt.trace_type='NORMAL'",
                tenantId,
                projectId,
                "rt");
        long gapTraces = count(
                "SELECT COUNT(*) FROM rag_trace rt WHERE rt.trace_type='NORMAL' AND rt.possible_gap=TRUE",
                tenantId,
                projectId,
                "rt");
        long resolvedGaps = count("""
                SELECT COUNT(DISTINCT f.trace_id)
                FROM feedback f
                JOIN rag_trace rt ON rt.trace_id=f.trace_id
                WHERE rt.trace_type='NORMAL' AND rt.possible_gap=TRUE
                  AND f.corrected_answer IS NOT NULL
                """, tenantId, projectId, "rt");
        EvalGovernanceSnapshot snapshot = new EvalGovernanceSnapshot(
                null,
                tenantId,
                projectId,
                snapshotDate,
                totalCandidates,
                approvedCandidates,
                rejectedCandidates,
                ratio(approvedCandidates, approvedCandidates + rejectedCandidates),
                ratio(reusedKnowledge, totalKnowledge),
                ratio(usedLowTrust, usedTotal),
                ratio(privacyEvents, normalTraces),
                ratio(resolvedGaps, gapTraces),
                createdAt);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO eval_governance_snapshot (
                    tenant_id, project_id, snapshot_date, total_candidate_count,
                    approved_candidate_count, rejected_candidate_count,
                    candidate_approval_rate, knowledge_reuse_rate,
                    contamination_rate, privacy_leakage_rate,
                    gap_resolve_rate, created_at
                ) VALUES (
                    :tenantId, :projectId, :snapshotDate, :totalCandidateCount,
                    :approvedCandidateCount, :rejectedCandidateCount,
                    :candidateApprovalRate, :knowledgeReuseRate,
                    :contaminationRate, :privacyLeakageRate,
                    :gapResolveRate, :createdAt
                )
                """, governanceParameters(snapshot), keys, new String[]{"id"});
        return snapshot.withId(key(keys, "eval governance snapshot"));
    }

    @Override
    public Optional<EvalGovernanceSnapshot> latestGovernanceSnapshot(String tenantId, String projectId) {
        StringBuilder sql = new StringBuilder("SELECT * FROM eval_governance_snapshot WHERE 1=1");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        appendScope(sql, parameters, tenantId, projectId);
        sql.append(" ORDER BY snapshot_date DESC, id DESC LIMIT 1");
        return queryOne(sql.toString(), parameters, this::mapGovernanceSnapshot);
    }

    @Override
    public List<EvalGovernanceSnapshot> listGovernanceSnapshots(
            String tenantId,
            String projectId,
            LocalDate from,
            LocalDate to) {
        StringBuilder sql = new StringBuilder("SELECT * FROM eval_governance_snapshot WHERE 1=1");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        appendScope(sql, parameters, tenantId, projectId);
        if (from != null) {
            sql.append(" AND snapshot_date>=:from");
            parameters.addValue("from", Date.valueOf(from));
        }
        if (to != null) {
            sql.append(" AND snapshot_date<=:to");
            parameters.addValue("to", Date.valueOf(to));
        }
        sql.append(" ORDER BY snapshot_date ASC, id ASC");
        return jdbc.query(sql.toString(), parameters, this::mapGovernanceSnapshot);
    }

    private MapSqlParameterSource datasetParameters(EvalDataset dataset) {
        return new MapSqlParameterSource()
                .addValue("name", dataset.name())
                .addValue("description", dataset.description())
                .addValue("tenantId", dataset.tenantId())
                .addValue("projectId", dataset.projectId())
                .addValue("createdBy", dataset.createdBy())
                .addValue("enabled", dataset.enabled())
                .addValue("createdAt", timestamp(dataset.createdAt()))
                .addValue("updatedAt", timestamp(dataset.updatedAt()));
    }

    private MapSqlParameterSource caseParameters(EvalCase evalCase) {
        return new MapSqlParameterSource()
                .addValue("datasetId", evalCase.datasetId())
                .addValue("question", evalCase.question())
                .addValue("expectedAnswer", evalCase.expectedAnswer())
                .addValue("tenantId", evalCase.tenantId())
                .addValue("projectId", evalCase.projectId())
                .addValue("userId", evalCase.userId())
                .addValue("conversationId", evalCase.conversationId())
                .addValue("tags", json(evalCase.tags()))
                .addValue("difficulty", evalCase.difficulty())
                .addValue("enabled", evalCase.enabled())
                .addValue("createdAt", timestamp(evalCase.createdAt()))
                .addValue("updatedAt", timestamp(evalCase.updatedAt()));
    }

    private MapSqlParameterSource runParameters(EvalRun run) {
        return new MapSqlParameterSource()
                .addValue("datasetId", run.datasetId())
                .addValue("runName", run.runName())
                .addValue("runType", run.runType().name())
                .addValue("beforeAfterGroup", run.beforeAfterGroup().name())
                .addValue("status", run.status().name())
                .addValue("totalCount", run.totalCount())
                .addValue("successCount", run.successCount())
                .addValue("failedCount", run.failedCount())
                .addValue("engineConfigSnapshot", run.engineConfigSnapshot())
                .addValue("modelConfigSnapshot", run.modelConfigSnapshot())
                .addValue("knowledgeSnapshotTime", timestamp(run.knowledgeSnapshotTime()))
                .addValue("startedAt", timestamp(run.startedAt()))
                .addValue("finishedAt", timestamp(run.finishedAt()))
                .addValue("errorMessage", run.errorMessage())
                .addValue("createdBy", run.createdBy())
                .addValue("createdAt", timestamp(run.createdAt()));
    }

    private MapSqlParameterSource resultParameters(EvalResult result) {
        return new MapSqlParameterSource()
                .addValue("evalRunId", result.evalRunId())
                .addValue("evalCaseId", result.evalCaseId())
                .addValue("traceId", result.traceId())
                .addValue("question", result.question())
                .addValue("expectedAnswer", result.expectedAnswer())
                .addValue("answer", result.answer())
                .addValue("retrievedCount", result.retrievedCount())
                .addValue("expectedKnowledgeCount", result.expectedKnowledgeCount())
                .addValue("recallAt5", result.recallAt5())
                .addValue("recallAt10", result.recallAt10())
                .addValue("precisionAt5", result.precisionAt5())
                .addValue("precisionAt10", result.precisionAt10())
                .addValue("mrr", result.mrr())
                .addValue("ndcgAt5", result.ndcgAt5())
                .addValue("ndcgAt10", result.ndcgAt10())
                .addValue("faithfulness", result.faithfulness())
                .addValue("answerCorrectness", result.answerCorrectness())
                .addValue("answerRelevance", result.answerRelevance())
                .addValue("hallucinationScore", result.hallucinationScore())
                .addValue("promptTokens", result.promptTokens())
                .addValue("completionTokens", result.completionTokens())
                .addValue("latencyMs", result.latencyMs())
                .addValue("status", result.status().name())
                .addValue("errorMessage", result.errorMessage())
                .addValue("createdAt", timestamp(result.createdAt()));
    }

    private MapSqlParameterSource judgeParameters(EvalJudgeDetail detail) {
        return new MapSqlParameterSource()
                .addValue("evalResultId", detail.evalResultId())
                .addValue("evalRunId", detail.evalRunId())
                .addValue("evalCaseId", detail.evalCaseId())
                .addValue("judgeType", detail.judgeType().name())
                .addValue("model", detail.model())
                .addValue("prompt", detail.prompt())
                .addValue("rawOutput", detail.rawOutput())
                .addValue("score", detail.score())
                .addValue("passed", detail.passed())
                .addValue("reason", detail.reason())
                .addValue("createdAt", timestamp(detail.createdAt()));
    }

    private MapSqlParameterSource reportParameters(EvalReport report) {
        return new MapSqlParameterSource()
                .addValue("evalRunId", report.evalRunId())
                .addValue("datasetId", report.datasetId())
                .addValue("totalCount", report.totalCount())
                .addValue("successCount", report.successCount())
                .addValue("failedCount", report.failedCount())
                .addValue("avgRecallAt5", report.avgRecallAt5())
                .addValue("avgRecallAt10", report.avgRecallAt10())
                .addValue("avgPrecisionAt5", report.avgPrecisionAt5())
                .addValue("avgPrecisionAt10", report.avgPrecisionAt10())
                .addValue("avgMrr", report.avgMrr())
                .addValue("avgNdcgAt5", report.avgNdcgAt5())
                .addValue("avgNdcgAt10", report.avgNdcgAt10())
                .addValue("avgFaithfulness", report.avgFaithfulness())
                .addValue("avgAnswerCorrectness", report.avgAnswerCorrectness())
                .addValue("avgAnswerRelevance", report.avgAnswerRelevance())
                .addValue("avgHallucinationScore", report.avgHallucinationScore())
                .addValue("avgLatencyMs", report.avgLatencyMs())
                .addValue("p90LatencyMs", report.p90LatencyMs())
                .addValue("summaryJson", report.summaryJson())
                .addValue("createdAt", timestamp(report.createdAt()));
    }

    private MapSqlParameterSource compareParameters(EvalCompareReport report) {
        return new MapSqlParameterSource()
                .addValue("beforeRunId", report.beforeRunId())
                .addValue("afterRunId", report.afterRunId())
                .addValue("recallAt10Delta", report.recallAt10Delta())
                .addValue("mrrDelta", report.mrrDelta())
                .addValue("faithfulnessDelta", report.faithfulnessDelta())
                .addValue("answerCorrectnessDelta", report.answerCorrectnessDelta())
                .addValue("hallucinationScoreDelta", report.hallucinationScoreDelta())
                .addValue("conclusion", report.conclusion())
                .addValue("createdAt", timestamp(report.createdAt()));
    }

    private MapSqlParameterSource governanceParameters(EvalGovernanceSnapshot snapshot) {
        return new MapSqlParameterSource()
                .addValue("tenantId", snapshot.tenantId())
                .addValue("projectId", snapshot.projectId())
                .addValue("snapshotDate", Date.valueOf(snapshot.snapshotDate()))
                .addValue("totalCandidateCount", snapshot.totalCandidateCount())
                .addValue("approvedCandidateCount", snapshot.approvedCandidateCount())
                .addValue("rejectedCandidateCount", snapshot.rejectedCandidateCount())
                .addValue("candidateApprovalRate", snapshot.candidateApprovalRate())
                .addValue("knowledgeReuseRate", snapshot.knowledgeReuseRate())
                .addValue("contaminationRate", snapshot.contaminationRate())
                .addValue("privacyLeakageRate", snapshot.privacyLeakageRate())
                .addValue("gapResolveRate", snapshot.gapResolveRate())
                .addValue("createdAt", timestamp(snapshot.createdAt()));
    }

    private EvalDataset mapDataset(ResultSet rs, int rowNum) throws SQLException {
        return new EvalDataset(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("tenant_id"),
                rs.getString("project_id"),
                rs.getString("created_by"),
                rs.getBoolean("enabled"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private EvalCase mapCase(ResultSet rs, int rowNum) throws SQLException {
        return new EvalCase(
                rs.getLong("id"),
                rs.getLong("dataset_id"),
                rs.getString("question"),
                rs.getString("expected_answer"),
                rs.getString("tenant_id"),
                rs.getString("project_id"),
                rs.getString("user_id"),
                rs.getString("conversation_id"),
                tags(rs.getString("tags")),
                rs.getString("difficulty"),
                rs.getBoolean("enabled"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private ExpectedKnowledge mapExpectedKnowledge(ResultSet rs, int rowNum) throws SQLException {
        return new ExpectedKnowledge(
                rs.getLong("id"),
                rs.getLong("eval_case_id"),
                rs.getLong("knowledge_id"),
                rs.getInt("relevance_grade"),
                instant(rs, "created_at"));
    }

    private EvalRun mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new EvalRun(
                rs.getLong("id"),
                rs.getLong("dataset_id"),
                rs.getString("run_name"),
                EvalRunType.valueOf(rs.getString("run_type")),
                BeforeAfterGroup.valueOf(rs.getString("before_after_group")),
                EvalRunStatus.valueOf(rs.getString("status")),
                rs.getInt("total_count"),
                rs.getInt("success_count"),
                rs.getInt("failed_count"),
                rs.getString("engine_config_snapshot"),
                rs.getString("model_config_snapshot"),
                instant(rs, "knowledge_snapshot_time"),
                instant(rs, "started_at"),
                instant(rs, "finished_at"),
                rs.getString("error_message"),
                rs.getString("created_by"),
                instant(rs, "created_at"));
    }

    private EvalResult mapResult(ResultSet rs, int rowNum) throws SQLException {
        return new EvalResult(
                rs.getLong("id"),
                rs.getLong("eval_run_id"),
                rs.getLong("eval_case_id"),
                rs.getString("trace_id"),
                rs.getString("question"),
                rs.getString("expected_answer"),
                rs.getString("answer"),
                rs.getInt("retrieved_count"),
                rs.getInt("expected_knowledge_count"),
                nullableDouble(rs, "recall_at_5"),
                nullableDouble(rs, "recall_at_10"),
                nullableDouble(rs, "precision_at_5"),
                nullableDouble(rs, "precision_at_10"),
                nullableDouble(rs, "mrr"),
                nullableDouble(rs, "ndcg_at_5"),
                nullableDouble(rs, "ndcg_at_10"),
                nullableDouble(rs, "faithfulness"),
                nullableDouble(rs, "answer_correctness"),
                nullableDouble(rs, "answer_relevance"),
                nullableDouble(rs, "hallucination_score"),
                rs.getInt("prompt_tokens"),
                rs.getInt("completion_tokens"),
                rs.getLong("latency_ms"),
                EvalResultStatus.valueOf(rs.getString("status")),
                rs.getString("error_message"),
                instant(rs, "created_at"));
    }

    private EvalJudgeDetail mapJudgeDetail(ResultSet rs, int rowNum) throws SQLException {
        return new EvalJudgeDetail(
                rs.getLong("id"),
                rs.getLong("eval_result_id"),
                rs.getLong("eval_run_id"),
                rs.getLong("eval_case_id"),
                JudgeType.valueOf(rs.getString("judge_type")),
                rs.getString("model"),
                rs.getString("prompt"),
                rs.getString("raw_output"),
                nullableDouble(rs, "score"),
                nullableBoolean(rs, "passed"),
                rs.getString("reason"),
                instant(rs, "created_at"));
    }

    private EvalReport mapReport(ResultSet rs, int rowNum) throws SQLException {
        return new EvalReport(
                rs.getLong("id"),
                rs.getLong("eval_run_id"),
                rs.getLong("dataset_id"),
                rs.getInt("total_count"),
                rs.getInt("success_count"),
                rs.getInt("failed_count"),
                nullableDouble(rs, "avg_recall_at_5"),
                nullableDouble(rs, "avg_recall_at_10"),
                nullableDouble(rs, "avg_precision_at_5"),
                nullableDouble(rs, "avg_precision_at_10"),
                nullableDouble(rs, "avg_mrr"),
                nullableDouble(rs, "avg_ndcg_at_5"),
                nullableDouble(rs, "avg_ndcg_at_10"),
                nullableDouble(rs, "avg_faithfulness"),
                nullableDouble(rs, "avg_answer_correctness"),
                nullableDouble(rs, "avg_answer_relevance"),
                nullableDouble(rs, "avg_hallucination_score"),
                nullableDouble(rs, "avg_latency_ms"),
                nullableDouble(rs, "p90_latency_ms"),
                rs.getString("summary_json"),
                instant(rs, "created_at"));
    }

    private EvalGovernanceSnapshot mapGovernanceSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new EvalGovernanceSnapshot(
                rs.getLong("id"),
                rs.getString("tenant_id"),
                rs.getString("project_id"),
                rs.getDate("snapshot_date").toLocalDate(),
                rs.getLong("total_candidate_count"),
                rs.getLong("approved_candidate_count"),
                rs.getLong("rejected_candidate_count"),
                nullableDouble(rs, "candidate_approval_rate"),
                nullableDouble(rs, "knowledge_reuse_rate"),
                nullableDouble(rs, "contamination_rate"),
                nullableDouble(rs, "privacy_leakage_rate"),
                nullableDouble(rs, "gap_resolve_rate"),
                instant(rs, "created_at"));
    }

    private <T> Optional<T> queryOne(String sql, Map<String, ?> parameters, RowMapper<T> rowMapper) {
        return jdbc.query(sql, parameters, rowMapper).stream().findFirst();
    }

    private <T> Optional<T> queryOne(String sql, MapSqlParameterSource parameters, RowMapper<T> rowMapper) {
        return jdbc.query(sql, parameters, rowMapper).stream().findFirst();
    }

    private long count(String baseSql, String tenantId, String projectId, String alias) {
        StringBuilder sql = new StringBuilder(baseSql);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        appendScope(sql, parameters, tenantId, projectId, alias);
        Long count = jdbc.queryForObject(sql.toString(), parameters, Long.class);
        return count == null ? 0 : count;
    }

    private void appendScope(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            String tenantId,
            String projectId) {
        if (tenantId != null && !tenantId.isBlank()) {
            sql.append(" AND tenant_id=:tenantId");
            parameters.addValue("tenantId", tenantId);
        }
        if (projectId != null && !projectId.isBlank()) {
            sql.append(" AND project_id=:projectId");
            parameters.addValue("projectId", projectId);
        }
    }

    private void appendScope(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            String tenantId,
            String projectId,
            String alias) {
        String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
        if (tenantId != null && !tenantId.isBlank()) {
            sql.append(" AND ").append(prefix).append("tenant_id=:tenantId");
            parameters.addValue("tenantId", tenantId);
        }
        if (projectId != null && !projectId.isBlank()) {
            sql.append(" AND ").append(prefix).append("project_id=:projectId");
            parameters.addValue("projectId", projectId);
        }
    }

    private MapSqlParameterSource limitOffset(int limit, int offset) {
        return new MapSqlParameterSource()
                .addValue("limit", Math.max(1, limit))
                .addValue("offset", Math.max(0, offset));
    }

    private Long key(KeyHolder keys, String label) {
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a generated " + label + " id");
        }
        return key.longValue();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Evaluation JSON serialization failed", exception);
        }
    }

    private List<String> tags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Evaluation tags JSON deserialization failed", exception);
        }
    }

    private Double ratio(long numerator, long denominator) {
        return denominator <= 0 ? null : numerator / (double) denominator;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
