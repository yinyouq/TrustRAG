package io.github.trustrag.storage.jdbc;

import io.github.trustrag.core.model.RagFeedbackRequest;
import io.github.trustrag.core.spi.FeedbackRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Timestamp;
import java.time.Instant;

public final class JdbcFeedbackRepository implements FeedbackRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFeedbackRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long save(RagFeedbackRequest request) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO feedback (
                    trace_id, user_id, feedback_type, feedback_content,
                    corrected_answer, processed, created_at
                ) VALUES (
                    :traceId, :userId, :feedbackType, :feedbackContent,
                    :correctedAnswer, :processed, :createdAt
                )
                """,
                new MapSqlParameterSource()
                        .addValue("traceId", request.traceId())
                        .addValue("userId", request.userId())
                        .addValue("feedbackType", request.feedbackType().name())
                        .addValue("feedbackContent", request.feedbackContent())
                        .addValue("correctedAnswer", request.correctedAnswer())
                        .addValue("processed", false)
                        .addValue("createdAt", Timestamp.from(Instant.now())),
                keys,
                new String[]{"id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a generated feedback id");
        }
        return key.longValue();
    }
}
