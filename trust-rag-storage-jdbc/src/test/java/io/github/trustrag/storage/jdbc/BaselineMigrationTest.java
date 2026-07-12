package io.github.trustrag.storage.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that each published V1 baseline can initialize an empty database.
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
    void postgresqlBaselineInitializesEmptyDatabase() throws Exception {
        verifyBaseline(
                "jdbc:h2:mem:trust_rag_postgresql_baseline;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "db/migration/V1__trust_rag_schema.sql");
    }

    @Test
    void mysqlBaselineInitializesEmptyDatabase() throws Exception {
        verifyBaseline(
                "jdbc:h2:mem:trust_rag_mysql_baseline;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "db/mysql/V1__trust_rag_schema.sql");
    }

    private void verifyBaseline(String jdbcUrl, String scriptPath) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(scriptPath));
            assertThat(readTables(connection)).containsAll(EXPECTED_TABLES);
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
}
