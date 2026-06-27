package io.github.trustrag.evaluation;

import java.time.Instant;
import java.time.LocalDate;

/**
 * EvalGovernanceSnapshot 表示某一时刻的治理指标快照，用于趋势分析。
 */
public record EvalGovernanceSnapshot(
        Long id,
        String tenantId,
        String projectId,
        LocalDate snapshotDate,
        long totalCandidateCount,
        long approvedCandidateCount,
        long rejectedCandidateCount,
        Double candidateApprovalRate,
        Double knowledgeReuseRate,
        Double contaminationRate,
        Double privacyLeakageRate,
        Double gapResolveRate,
        Instant createdAt) {

    public EvalGovernanceSnapshot withId(Long value) {
        return new EvalGovernanceSnapshot(
                value, tenantId, projectId, snapshotDate, totalCandidateCount,
                approvedCandidateCount, rejectedCandidateCount, candidateApprovalRate,
                knowledgeReuseRate, contaminationRate, privacyLeakageRate, gapResolveRate, createdAt);
    }
}
