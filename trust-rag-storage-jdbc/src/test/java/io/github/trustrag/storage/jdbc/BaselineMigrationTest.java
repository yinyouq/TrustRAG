package io.github.trustrag.storage.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the immutable V1 baseline plus forward migrations can initialize an empty database.
 */
class BaselineMigrationTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "knowledge_item",
            "rag_trace",
            "retrieval_log",
            "feedback",
            "review_task",
            "promotion_task",
            "conflict_record",
            "knowledge_lineage",
            "index_sync_task",
            "document_import_task",
            "eval_dataset",
            "eval_case",
            "eval_case_expected_knowledge",
            "eval_run",
            "eval_result",
            "eval_judge_detail",
            "eval_report",
            "eval_compare_report",
            "eval_governance_snapshot",
            "privacy_event");

    @Test
    void postgresqlMigrationsInitializeEmptyDatabase() throws Exception {
        verifyMigrations(
                "jdbc:h2:mem:trust_rag_postgresql_baseline;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "db/migration/V1__trust_rag_schema.sql",
                "db/migration/V2__three_pool_governance.sql",
                "db/migration/V3__hybrid_retrieval.sql",
                "db/migration/V4__document_ingestion.sql",
                "db/migration/V5__evaluation_system.sql",
                "db/migration/V20260712_0025__widen_retrieval_raw_scores.sql.sql",
                "db/migration/V20260712_1532__widen_promotion_stage.sql");
    }

    @Test
    void mysqlMigrationsInitializeEmptyDatabase() throws Exception {
        verifyMigrations(
                "jdbc:h2:mem:trust_rag_mysql_baseline;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "db/mysql/V1__trust_rag_schema.sql",
                "db/mysql/V2__three_pool_governance.sql",
                "db/mysql/V3__hybrid_retrieval.sql",
                "db/mysql/V4__document_ingestion.sql",
                "db/mysql/V5__evaluation_system.sql",
                "db/mysql/V20260712_0025__widen_retrieval_raw_scores.sql.sql",
                "db/mysql/V20260712_1532__widen_promotion_stage.sql");
    }

    private void verifyMigrations(String jdbcUrl, String... scriptPaths) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            for (String scriptPath : scriptPaths) {
                executeMigration(connection, scriptPath);
            }
            assertThat(readTables(connection)).containsAll(EXPECTED_TABLES);
            assertThat(readColumns(connection, "knowledge_item"))
                    .contains("promotion_stage", "document_id", "claim_hash");
            assertThat(readColumns(connection, "retrieval_log"))
                    .contains("vector_rank", "keyword_rank", "rrf_score");
        }
    }

    /**
     * H2 does not support the production scripts' multi-column ALTER TABLE form.
     * Keep those historical Flyway resources immutable and execute their equivalent
     * single-column statements only in this H2 baseline test.
     */
    private void executeMigration(Connection connection, String scriptPath) throws Exception {
        if (!scriptPath.contains("V20260712_0025__widen_retrieval_raw_scores")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(scriptPath));
            return;
        }

        boolean mysql = scriptPath.startsWith("db/mysql/");
        try (Statement statement = connection.createStatement()) {
            if (mysql) {
                statement.execute("ALTER TABLE rag_trace MODIFY COLUMN max_vector_score DECIMAL(16,6) NULL");
                statement.execute("ALTER TABLE rag_trace MODIFY COLUMN avg_vector_score DECIMAL(16,6) NULL");
                statement.execute("ALTER TABLE rag_trace MODIFY COLUMN max_rerank_score DECIMAL(16,6) NULL");
                statement.execute("ALTER TABLE retrieval_log MODIFY COLUMN vector_score DECIMAL(16,6) NULL");
                statement.execute("ALTER TABLE retrieval_log MODIFY COLUMN keyword_score DECIMAL(16,6) NULL");
                statement.execute("ALTER TABLE retrieval_log MODIFY COLUMN rerank_score DECIMAL(16,6) NULL");
            } else {
                statement.execute("ALTER TABLE rag_trace ALTER COLUMN max_vector_score TYPE NUMERIC(16,6)");
                statement.execute("ALTER TABLE rag_trace ALTER COLUMN avg_vector_score TYPE NUMERIC(16,6)");
                statement.execute("ALTER TABLE rag_trace ALTER COLUMN max_rerank_score TYPE NUMERIC(16,6)");
                statement.execute("ALTER TABLE retrieval_log ALTER COLUMN vector_score TYPE NUMERIC(16,6)");
                statement.execute("ALTER TABLE retrieval_log ALTER COLUMN keyword_score TYPE NUMERIC(16,6)");
                statement.execute("ALTER TABLE retrieval_log ALTER COLUMN rerank_score TYPE NUMERIC(16,6)");
            }
        }
    }

    private Set<String> readTables(Connection connection) throws Exception {
        Set<String> tables = new HashSet<>();
        try (ResultSet resultSet = connection.getMetaData()
                .getTables(null, null, null, new String[]{"TABLE"})) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return tables;
    }

    private Set<String> readColumns(Connection connection, String table) throws Exception {
        Set<String> columns = new HashSet<>();
        try (ResultSet resultSet = connection.getMetaData().getColumns(null, null, table, null)) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }
}
