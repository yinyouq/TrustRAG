package io.github.trustrag.storage.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.model.KnowledgeGovernance;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.spi.KnowledgeRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * knowledge_item 表的 JDBC 仓储实现。
 *
 * <p>关系库保存知识状态、治理指标和来源元数据，是外部检索索引之外的权威状态源。</p>
 */
public final class JdbcKnowledgeRepository implements KnowledgeRepository {

    private static final String COLUMNS = """
            title, claim, content, summary, knowledge_type, trust_level, status, scope_type,
            user_id, conversation_id, project_id, tenant_id, source_type, source_ref, evidence,
            embedding_id, embedding_model, embedding_dimension, confidence, privacy_score,
            version, hash, approved_by, approved_at, reject_reason, created_at, updated_at, expires_at,
            promotion_stage, promotion_score, llm_pre_review_result, normalized_claim, claim_hash,
            llm_score, source_score, evidence_score, feedback_score, usage_score, general_value_score,
            privacy_risk, conflict_risk, stale_risk, applicable_version, valid_from, valid_to,
            source_time, last_verified_at, usage_count, positive_feedback_count,
            negative_feedback_count, tags_json, previous_trust_level, previous_status,
            source_title, source_url, page_number, section_path, document_id, chunk_index
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<KnowledgeItem> rowMapper = this::mapRow;

    public JdbcKnowledgeRepository(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, new ObjectMapper());
    }

    public JdbcKnowledgeRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public KnowledgeItem save(KnowledgeItem knowledge) {
        String sql = "INSERT INTO knowledge_item (" + COLUMNS + ") VALUES ("
                + ":title, :claim, :content, :summary, :knowledgeType, :trustLevel, :status, :scopeType,"
                + ":userId, :conversationId, :projectId, :tenantId, :sourceType, :sourceRef, :evidence,"
                + ":embeddingId, :embeddingModel, :embeddingDimension, :confidence, :privacyScore,"
                + ":version, :hash, :approvedBy, :approvedAt, :rejectReason, :createdAt, :updatedAt, :expiresAt,"
                + ":promotionStage, :promotionScore, :llmPreReviewResult, :normalizedClaim, :claimHash,"
                + ":llmScore, :sourceScore, :evidenceScore, :feedbackScore, :usageScore, :generalValueScore,"
                + ":privacyRisk, :conflictRisk, :staleRisk, :applicableVersion, :validFrom, :validTo,"
                + ":sourceTime, :lastVerifiedAt, :usageCount, :positiveFeedbackCount,"
                + ":negativeFeedbackCount, :tagsJson, :previousTrustLevel, :previousStatus,"
                + ":sourceTitle, :sourceUrl, :pageNumber, :sectionPath, :documentId, :chunkIndex)";
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(sql, parameters(knowledge), keys, new String[]{"id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a generated knowledge id");
        }
        return knowledge.withId(key.longValue());
    }

    @Override
    public void update(KnowledgeItem knowledge) {
        String sql = updateSql("WHERE id=:id");
        MapSqlParameterSource parameters = parameters(knowledge).addValue("id", knowledge.id());
        if (jdbc.update(sql, parameters) != 1) {
            throw new IllegalStateException("Knowledge update affected no row: " + knowledge.id());
        }
    }

    @Override
    public boolean updateIfState(
            KnowledgeItem knowledge,
            KnowledgeStatus expectedStatus,
            int expectedVersion) {
        // 异步索引、晋升和审核都依赖状态+版本双条件，防止旧任务覆盖新状态。
        String sql = updateSql(
                "WHERE id=:id AND status=:expectedStatus AND version=:expectedVersion");
        MapSqlParameterSource parameters = parameters(knowledge)
                .addValue("id", knowledge.id())
                .addValue("expectedStatus", expectedStatus.name())
                .addValue("expectedVersion", expectedVersion);
        return jdbc.update(sql, parameters) == 1;
    }

    @Override
    public Optional<KnowledgeItem> findById(long id) {
        return queryOne("SELECT * FROM knowledge_item WHERE id=:id", Map.of("id", id));
    }

    @Override
    public Optional<KnowledgeItem> findByHash(String hash) {
        return queryOne("SELECT * FROM knowledge_item WHERE hash=:hash", Map.of("hash", hash));
    }

    @Override
    public Optional<KnowledgeItem> findByClaimHash(String claimHash, long excludedKnowledgeId) {
        return queryOne("""
                        SELECT * FROM knowledge_item
                        WHERE claim_hash=:claimHash AND id<>:excludedId
                          AND status NOT IN ('REJECTED', 'EXPIRED', 'ROLLBACK')
                        ORDER BY trust_level ASC, updated_at DESC
                        LIMIT 1
                        """,
                Map.of("claimHash", claimHash, "excludedId", excludedKnowledgeId));
    }

    @Override
    public List<KnowledgeItem> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT * FROM knowledge_item WHERE id IN (:ids)", Map.of("ids", ids), rowMapper);
    }

    @Override
    public List<KnowledgeItem> findCandidates(
            KnowledgeStatus status,
            TrustLevel trustLevel,
            ScopeType scopeType,
            int limit,
            int offset) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM knowledge_item WHERE status=:status");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("status", status.name())
                .addValue("limit", limit)
                .addValue("offset", offset);
        if (trustLevel != null) {
            sql.append(" AND trust_level=:trustLevel");
            parameters.addValue("trustLevel", trustLevel.name());
        }
        if (scopeType != null) {
            sql.append(" AND scope_type=:scopeType");
            parameters.addValue("scopeType", scopeType.name());
        }
        sql.append(" ORDER BY created_at ASC LIMIT :limit OFFSET :offset");
        return jdbc.query(sql.toString(), parameters, rowMapper);
    }

    @Override
    public List<KnowledgeItem> findByTrustAndStatuses(
            TrustLevel trustLevel,
            Set<KnowledgeStatus> statuses,
            int limit,
            int offset) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                        SELECT * FROM knowledge_item
                        WHERE trust_level=:trustLevel AND status IN (:statuses)
                        ORDER BY updated_at DESC
                        LIMIT :limit OFFSET :offset
                        """,
                new MapSqlParameterSource()
                        .addValue("trustLevel", trustLevel.name())
                        .addValue("statuses", statuses.stream().map(Enum::name).toList())
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                rowMapper);
    }

    @Override
    public List<KnowledgeItem> findPromotionCandidates(int limit) {
        return jdbc.query("""
                        SELECT * FROM knowledge_item
                        WHERE trust_level='LOW'
                          AND status IN ('LOW_PENDING', 'LOW_ENABLED', 'PROMOTION_PENDING')
                        ORDER BY created_at ASC
                        LIMIT :limit
                        """,
                Map.of("limit", limit),
                rowMapper);
    }

    @Override
    public List<KnowledgeItem> findExpired(Instant now, int limit) {
        return jdbc.query("""
                        SELECT * FROM knowledge_item
                        WHERE status IN ('LOW_ENABLED', 'MEDIUM_ENABLED', 'HUMAN_REVIEW_PENDING', 'HIGH_ENABLED')
                          AND ((valid_to IS NOT NULL AND valid_to<:now)
                            OR (expires_at IS NOT NULL AND expires_at<:now))
                        ORDER BY updated_at ASC
                        LIMIT :limit
                        """,
                new MapSqlParameterSource()
                        .addValue("now", timestamp(now))
                        .addValue("limit", limit),
                rowMapper);
    }

    @Override
    public List<KnowledgeItem> findNegativeFeedbackCandidates(int threshold, int limit) {
        return jdbc.query("""
                        SELECT * FROM knowledge_item
                        WHERE status IN ('MEDIUM_ENABLED', 'HUMAN_REVIEW_PENDING', 'HIGH_ENABLED')
                          AND negative_feedback_count>=:threshold
                        ORDER BY negative_feedback_count DESC
                        LIMIT :limit
                        """,
                Map.of("threshold", threshold, "limit", limit),
                rowMapper);
    }

    @Override
    public void incrementUsageCount(long knowledgeId) {
        jdbc.update("""
                UPDATE knowledge_item
                SET usage_count=usage_count+1, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id
                """, Map.of("id", knowledgeId));
    }

    @Override
    public void incrementFeedbackCounts(
            Collection<Long> knowledgeIds,
            boolean positive,
            boolean negative) {
        if (knowledgeIds == null || knowledgeIds.isEmpty() || (!positive && !negative)) {
            return;
        }
        jdbc.update("""
                        UPDATE knowledge_item
                        SET positive_feedback_count=positive_feedback_count+:positive,
                            negative_feedback_count=negative_feedback_count+:negative,
                            updated_at=CURRENT_TIMESTAMP
                        WHERE id IN (:ids)
                        """,
                new MapSqlParameterSource()
                        .addValue("positive", positive ? 1 : 0)
                        .addValue("negative", negative ? 1 : 0)
                        .addValue("ids", knowledgeIds));
    }

    private String updateSql(String whereClause) {
        return """
                UPDATE knowledge_item SET
                    title=:title, claim=:claim, content=:content, summary=:summary,
                    knowledge_type=:knowledgeType, trust_level=:trustLevel, status=:status, scope_type=:scopeType,
                    user_id=:userId, conversation_id=:conversationId, project_id=:projectId, tenant_id=:tenantId,
                    source_type=:sourceType, source_ref=:sourceRef, evidence=:evidence,
                    embedding_id=:embeddingId, embedding_model=:embeddingModel,
                    embedding_dimension=:embeddingDimension, confidence=:confidence, privacy_score=:privacyScore,
                    version=:version, hash=:hash, approved_by=:approvedBy, approved_at=:approvedAt,
                    reject_reason=:rejectReason, updated_at=:updatedAt, expires_at=:expiresAt,
                    promotion_stage=:promotionStage, promotion_score=:promotionScore,
                    llm_pre_review_result=:llmPreReviewResult, normalized_claim=:normalizedClaim,
                    claim_hash=:claimHash, llm_score=:llmScore, source_score=:sourceScore,
                    evidence_score=:evidenceScore, feedback_score=:feedbackScore,
                    usage_score=:usageScore, general_value_score=:generalValueScore,
                    privacy_risk=:privacyRisk, conflict_risk=:conflictRisk, stale_risk=:staleRisk,
                    applicable_version=:applicableVersion, valid_from=:validFrom, valid_to=:validTo,
                    source_time=:sourceTime, last_verified_at=:lastVerifiedAt,
                    usage_count=:usageCount, positive_feedback_count=:positiveFeedbackCount,
                    negative_feedback_count=:negativeFeedbackCount, tags_json=:tagsJson,
                    previous_trust_level=:previousTrustLevel, previous_status=:previousStatus,
                    source_title=:sourceTitle, source_url=:sourceUrl, page_number=:pageNumber,
                    section_path=:sectionPath, document_id=:documentId, chunk_index=:chunkIndex
                """ + whereClause;
    }

    private Optional<KnowledgeItem> queryOne(String sql, Map<String, ?> parameters) {
        List<KnowledgeItem> results = jdbc.query(sql, parameters, rowMapper);
        return results.stream().findFirst();
    }

    private MapSqlParameterSource parameters(KnowledgeItem value) {
        KnowledgeGovernance governance = value.governance();
        // KnowledgeItem 被拆成基础字段、governance 字段和 sourceMetadata 字段写入同一张表。
        return new MapSqlParameterSource()
                .addValue("title", value.title())
                .addValue("claim", value.claim())
                .addValue("content", value.content())
                .addValue("summary", value.summary())
                .addValue("knowledgeType", value.knowledgeType())
                .addValue("trustLevel", value.trustLevel().name())
                .addValue("status", value.status().name())
                .addValue("scopeType", value.scopeType().name())
                .addValue("userId", value.userId())
                .addValue("conversationId", value.conversationId())
                .addValue("projectId", value.projectId())
                .addValue("tenantId", value.tenantId())
                .addValue("sourceType", value.sourceType())
                .addValue("sourceRef", value.sourceRef())
                .addValue("evidence", value.evidence())
                .addValue("embeddingId", value.embeddingId())
                .addValue("embeddingModel", value.embeddingModel())
                .addValue("embeddingDimension", value.embeddingDimension())
                .addValue("confidence", value.confidence())
                .addValue("privacyScore", value.privacyScore())
                .addValue("version", value.version())
                .addValue("hash", value.hash())
                .addValue("approvedBy", value.approvedBy())
                .addValue("approvedAt", timestamp(value.approvedAt()))
                .addValue("rejectReason", value.rejectReason())
                .addValue("createdAt", timestamp(value.createdAt()))
                .addValue("updatedAt", timestamp(value.updatedAt()))
                .addValue("expiresAt", timestamp(value.expiresAt()))
                .addValue("promotionStage", governance.promotionStage())
                .addValue("promotionScore", governance.promotionScore())
                .addValue("llmPreReviewResult", governance.llmPreReviewResult())
                .addValue("normalizedClaim", governance.normalizedClaim())
                .addValue("claimHash", governance.claimHash())
                .addValue("llmScore", governance.llmScore())
                .addValue("sourceScore", governance.sourceScore())
                .addValue("evidenceScore", governance.evidenceScore())
                .addValue("feedbackScore", governance.feedbackScore())
                .addValue("usageScore", governance.usageScore())
                .addValue("generalValueScore", governance.generalValueScore())
                .addValue("privacyRisk", defaultZero(governance.privacyRisk()))
                .addValue("conflictRisk", defaultZero(governance.conflictRisk()))
                .addValue("staleRisk", defaultZero(governance.staleRisk()))
                .addValue("applicableVersion", governance.applicableVersion())
                .addValue("validFrom", timestamp(governance.validFrom()))
                .addValue("validTo", timestamp(governance.validTo()))
                .addValue("sourceTime", timestamp(governance.sourceTime()))
                .addValue("lastVerifiedAt", timestamp(governance.lastVerifiedAt()))
                .addValue("usageCount", governance.usageCount())
                .addValue("positiveFeedbackCount", governance.positiveFeedbackCount())
                .addValue("negativeFeedbackCount", governance.negativeFeedbackCount())
                .addValue("tagsJson", json(governance.tags()))
                .addValue("previousTrustLevel", enumName(governance.previousTrustLevel()))
                .addValue("previousStatus", enumName(governance.previousStatus()))
                .addValue("sourceTitle", value.sourceMetadata().sourceTitle())
                .addValue("sourceUrl", value.sourceMetadata().sourceUrl())
                .addValue("pageNumber", value.sourceMetadata().pageNumber())
                .addValue("sectionPath", value.sourceMetadata().sectionPath())
                .addValue("documentId", value.sourceMetadata().documentId())
                .addValue("chunkIndex", value.sourceMetadata().chunkIndex());
    }

    private KnowledgeItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        // 读库时重新组装不可变领域对象，业务层不感知表字段拆分细节。
        return new KnowledgeItem(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("claim"),
                rs.getString("content"),
                rs.getString("summary"),
                rs.getString("knowledge_type"),
                TrustLevel.valueOf(rs.getString("trust_level")),
                KnowledgeStatus.valueOf(rs.getString("status")),
                ScopeType.valueOf(rs.getString("scope_type")),
                rs.getString("user_id"),
                rs.getString("conversation_id"),
                rs.getString("project_id"),
                rs.getString("tenant_id"),
                rs.getString("source_type"),
                rs.getString("source_ref"),
                rs.getString("evidence"),
                rs.getString("embedding_id"),
                rs.getString("embedding_model"),
                nullableInteger(rs, "embedding_dimension"),
                nullableDouble(rs, "confidence"),
                nullableDouble(rs, "privacy_score"),
                rs.getInt("version"),
                rs.getString("hash"),
                rs.getString("approved_by"),
                instant(rs, "approved_at"),
                rs.getString("reject_reason"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "expires_at"),
                new KnowledgeGovernance(
                        rs.getString("promotion_stage"),
                        nullableDouble(rs, "promotion_score"),
                        rs.getString("llm_pre_review_result"),
                        rs.getString("normalized_claim"),
                        rs.getString("claim_hash"),
                        nullableDouble(rs, "llm_score"),
                        nullableDouble(rs, "source_score"),
                        nullableDouble(rs, "evidence_score"),
                        nullableDouble(rs, "feedback_score"),
                        nullableDouble(rs, "usage_score"),
                        nullableDouble(rs, "general_value_score"),
                        nullableDouble(rs, "privacy_risk"),
                        nullableDouble(rs, "conflict_risk"),
                        nullableDouble(rs, "stale_risk"),
                        rs.getString("applicable_version"),
                        instant(rs, "valid_from"),
                        instant(rs, "valid_to"),
                        instant(rs, "source_time"),
                        instant(rs, "last_verified_at"),
                        rs.getInt("usage_count"),
                        rs.getInt("positive_feedback_count"),
                        rs.getInt("negative_feedback_count"),
                        tags(rs.getString("tags_json")),
                        enumValue(TrustLevel.class, rs.getString("previous_trust_level")),
                        enumValue(KnowledgeStatus.class, rs.getString("previous_status"))),
                new io.github.trustrag.core.model.KnowledgeSourceMetadata(
                        rs.getString("source_title"),
                        rs.getString("source_url"),
                        nullableInteger(rs, "page_number"),
                        rs.getString("section_path"),
                        rs.getString("document_id"),
                        nullableInteger(rs, "chunk_index")));
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private double defaultZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null || value.isBlank() ? null : Enum.valueOf(type, value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Knowledge governance JSON serialization failed", exception);
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
            throw new IllegalStateException("Knowledge tags JSON deserialization failed", exception);
        }
    }
}
