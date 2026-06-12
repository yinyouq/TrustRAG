package io.github.trustrag.storage.jdbc;

import io.github.trustrag.core.model.ConflictRecord;
import io.github.trustrag.core.model.ConflictType;
import io.github.trustrag.core.spi.ConflictRecordRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class JdbcConflictRecordRepository implements ConflictRecordRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final RowMapper<ConflictRecord> rowMapper = this::mapRow;

    public JdbcConflictRecordRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ConflictRecord save(ConflictRecord record) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO conflict_record (
                    candidate_knowledge_id, existing_knowledge_id, conflict_type,
                    confidence, reason, created_at
                ) VALUES (
                    :candidateId, :existingId, :conflictType,
                    :confidence, :reason, :createdAt
                )
                """,
                new MapSqlParameterSource()
                        .addValue("candidateId", record.candidateKnowledgeId())
                        .addValue("existingId", record.existingKnowledgeId())
                        .addValue("conflictType", record.conflictType().name())
                        .addValue("confidence", record.confidence())
                        .addValue("reason", record.reason())
                        .addValue("createdAt", timestamp(record.createdAt())),
                keys,
                new String[]{"id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a conflict record id");
        }
        return new ConflictRecord(
                key.longValue(), record.candidateKnowledgeId(), record.existingKnowledgeId(),
                record.conflictType(), record.confidence(), record.reason(), record.createdAt());
    }

    @Override
    public List<ConflictRecord> findByCandidateKnowledgeId(long knowledgeId) {
        return jdbc.query("""
                        SELECT * FROM conflict_record
                        WHERE candidate_knowledge_id=:knowledgeId
                        ORDER BY created_at DESC
                        """,
                Map.of("knowledgeId", knowledgeId),
                rowMapper);
    }

    @Override
    public List<ConflictRecord> find(int limit, int offset) {
        return jdbc.query("""
                        SELECT * FROM conflict_record
                        ORDER BY created_at DESC
                        LIMIT :limit OFFSET :offset
                        """,
                Map.of("limit", limit, "offset", offset),
                rowMapper);
    }

    private ConflictRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ConflictRecord(
                rs.getLong("id"),
                rs.getLong("candidate_knowledge_id"),
                rs.getLong("existing_knowledge_id"),
                ConflictType.valueOf(rs.getString("conflict_type")),
                rs.getDouble("confidence"),
                rs.getString("reason"),
                instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
