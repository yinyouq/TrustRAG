package io.github.trustrag.storage.jdbc;

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

public final class JdbcKnowledgeRepository implements KnowledgeRepository {

    private static final String COLUMNS = """
            title, claim, content, summary, knowledge_type, trust_level, status, scope_type,
            user_id, conversation_id, project_id, tenant_id, source_type, source_ref, evidence,
            embedding_id, embedding_model, embedding_dimension, confidence, privacy_score,
            version, hash, approved_by, approved_at, reject_reason, created_at, updated_at, expires_at
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<KnowledgeItem> rowMapper = this::mapRow;

    public JdbcKnowledgeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public KnowledgeItem save(KnowledgeItem knowledge) {
        String sql = "INSERT INTO knowledge_item (" + COLUMNS + ") VALUES ("
                + ":title, :claim, :content, :summary, :knowledgeType, :trustLevel, :status, :scopeType,"
                + ":userId, :conversationId, :projectId, :tenantId, :sourceType, :sourceRef, :evidence,"
                + ":embeddingId, :embeddingModel, :embeddingDimension, :confidence, :privacyScore,"
                + ":version, :hash, :approvedBy, :approvedAt, :rejectReason, :createdAt, :updatedAt, :expiresAt)";
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
                    reject_reason=:rejectReason, updated_at=:updatedAt, expires_at=:expiresAt
                """ + whereClause;
    }

    private Optional<KnowledgeItem> queryOne(String sql, Map<String, ?> parameters) {
        List<KnowledgeItem> results = jdbc.query(sql, parameters, rowMapper);
        return results.stream().findFirst();
    }

    private MapSqlParameterSource parameters(KnowledgeItem value) {
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
                .addValue("expiresAt", timestamp(value.expiresAt()));
    }

    private KnowledgeItem mapRow(ResultSet rs, int rowNum) throws SQLException {
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
                instant(rs, "expires_at"));
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
}
