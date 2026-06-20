package io.github.trustrag.evaluation;

import java.time.Instant;
import java.time.LocalDate;

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
