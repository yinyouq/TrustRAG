package io.github.trustrag.core.service;

import io.github.trustrag.core.exception.InvalidKnowledgeStateException;
import io.github.trustrag.core.model.KnowledgeStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class KnowledgeStateMachine {

    private static final Map<KnowledgeStatus, Set<KnowledgeStatus>> TRANSITIONS = transitions();

    public void validate(KnowledgeStatus from, KnowledgeStatus to) {
        if (from == to) {
            return;
        }
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidKnowledgeStateException(
                    "Illegal knowledge state transition: " + from + " -> " + to);
        }
    }

    private static Map<KnowledgeStatus, Set<KnowledgeStatus>> transitions() {
        Map<KnowledgeStatus, Set<KnowledgeStatus>> values = new EnumMap<>(KnowledgeStatus.class);
        values.put(KnowledgeStatus.INDEXING, EnumSet.of(
                KnowledgeStatus.HIGH_ENABLED,
                KnowledgeStatus.MEDIUM_ENABLED,
                KnowledgeStatus.HUMAN_REVIEW_PENDING,
                KnowledgeStatus.LOW_ENABLED,
                KnowledgeStatus.INDEX_FAILED));
        values.put(KnowledgeStatus.LOW_PENDING, EnumSet.of(
                KnowledgeStatus.PROMOTION_PENDING,
                KnowledgeStatus.PROMOTION_RUNNING,
                KnowledgeStatus.LOW_ENABLED,
                KnowledgeStatus.REJECTED,
                KnowledgeStatus.MERGE_PENDING));
        values.put(KnowledgeStatus.LOW_ENABLED, EnumSet.of(
                KnowledgeStatus.PROMOTION_PENDING,
                KnowledgeStatus.PROMOTION_RUNNING,
                KnowledgeStatus.REJECTED,
                KnowledgeStatus.CONFLICT,
                KnowledgeStatus.EXPIRED,
                KnowledgeStatus.MERGE_PENDING,
                KnowledgeStatus.ROLLBACK));
        values.put(KnowledgeStatus.PROMOTION_PENDING, EnumSet.of(
                KnowledgeStatus.PROMOTION_RUNNING,
                KnowledgeStatus.LOW_ENABLED,
                KnowledgeStatus.REJECTED));
        values.put(KnowledgeStatus.PROMOTION_RUNNING, EnumSet.of(
                KnowledgeStatus.PROMOTION_PENDING,
                KnowledgeStatus.LOW_ENABLED,
                KnowledgeStatus.MEDIUM_ENABLED,
                KnowledgeStatus.HUMAN_REVIEW_PENDING,
                KnowledgeStatus.REJECTED,
                KnowledgeStatus.CONFLICT,
                KnowledgeStatus.EXPIRED,
                KnowledgeStatus.MERGE_PENDING,
                KnowledgeStatus.INDEX_FAILED));
        values.put(KnowledgeStatus.MEDIUM_ENABLED, EnumSet.of(
                KnowledgeStatus.HUMAN_REVIEW_PENDING,
                KnowledgeStatus.INDEXING,
                KnowledgeStatus.LOW_ENABLED,
                KnowledgeStatus.REJECTED,
                KnowledgeStatus.CONFLICT,
                KnowledgeStatus.EXPIRED,
                KnowledgeStatus.MERGE_PENDING,
                KnowledgeStatus.ROLLBACK));
        values.put(KnowledgeStatus.HUMAN_REVIEW_PENDING, EnumSet.of(
                KnowledgeStatus.INDEXING,
                KnowledgeStatus.LOW_ENABLED,
                KnowledgeStatus.REJECTED,
                KnowledgeStatus.CONFLICT,
                KnowledgeStatus.EXPIRED,
                KnowledgeStatus.MERGE_PENDING,
                KnowledgeStatus.ROLLBACK));
        values.put(KnowledgeStatus.HIGH_ENABLED, EnumSet.of(
                KnowledgeStatus.INDEXING,
                KnowledgeStatus.MEDIUM_ENABLED,
                KnowledgeStatus.EXPIRED,
                KnowledgeStatus.ROLLBACK,
                KnowledgeStatus.REJECTED));
        values.put(KnowledgeStatus.INDEX_FAILED, EnumSet.of(
                KnowledgeStatus.INDEXING,
                KnowledgeStatus.HIGH_ENABLED,
                KnowledgeStatus.MEDIUM_ENABLED,
                KnowledgeStatus.HUMAN_REVIEW_PENDING,
                KnowledgeStatus.LOW_ENABLED,
                KnowledgeStatus.PROMOTION_PENDING,
                KnowledgeStatus.PROMOTION_RUNNING,
                KnowledgeStatus.REJECTED));
        values.put(KnowledgeStatus.CONFLICT, EnumSet.of(
                KnowledgeStatus.PROMOTION_PENDING,
                KnowledgeStatus.REJECTED,
                KnowledgeStatus.MERGE_PENDING));
        values.put(KnowledgeStatus.EXPIRED, EnumSet.of(
                KnowledgeStatus.INDEXING,
                KnowledgeStatus.REJECTED));
        values.put(KnowledgeStatus.MERGE_PENDING, EnumSet.of(
                KnowledgeStatus.REJECTED,
                KnowledgeStatus.LOW_ENABLED,
                KnowledgeStatus.HUMAN_REVIEW_PENDING));
        values.put(KnowledgeStatus.ROLLBACK, EnumSet.of(
                KnowledgeStatus.INDEXING,
                KnowledgeStatus.HIGH_ENABLED,
                KnowledgeStatus.MEDIUM_ENABLED,
                KnowledgeStatus.HUMAN_REVIEW_PENDING,
                KnowledgeStatus.LOW_ENABLED,
                KnowledgeStatus.REJECTED));
        values.put(KnowledgeStatus.REJECTED, EnumSet.noneOf(KnowledgeStatus.class));
        return Map.copyOf(values);
    }
}
