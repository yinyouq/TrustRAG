package io.github.trustrag.evaluation;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 评估用例 CSV 导入服务。
 *
 * <p>逐行导入并收集行级错误，单行失败不会阻断其他测试用例入库。</p>
 */
public final class EvalCaseCsvImportService {

    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final int DEFAULT_EXPECTED_KNOWLEDGE_RELEVANCE_GRADE = 3;

    private final EvalDatasetService datasetService;
    private final EvalCaseService caseService;

    public EvalCaseCsvImportService(EvalDatasetService datasetService, EvalCaseService caseService) {
        this.datasetService = datasetService;
        this.caseService = caseService;
    }

    public EvalCaseCsvImportResult importCsv(
            long datasetId,
            Reader reader,
            ImportDefaults defaults) {
        datasetService.get(datasetId);
        if (reader == null) {
            throw new IllegalArgumentException("CSV reader must not be null");
        }
        ImportDefaults resolvedDefaults = defaults == null ? ImportDefaults.empty() : defaults;
        List<EvalCaseCsvImportResult.RowError> errors = new ArrayList<>();
        int total = 0;
        int imported = 0;
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        try (CSVParser parser = format.parse(withoutBom(reader))) {
            if (!parser.getHeaderMap().containsKey("question")) {
                throw new IllegalArgumentException("CSV header must contain question");
            }
            for (CSVRecord record : parser) {
                total++;
                try {
                    importRecord(datasetId, record, resolvedDefaults);
                    imported++;
                } catch (RuntimeException exception) {
                    errors.add(new EvalCaseCsvImportResult.RowError(
                            record.getRecordNumber() + 1,
                            message(exception)));
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("failed to read evaluation CSV", exception);
        }
        return new EvalCaseCsvImportResult(total, imported, errors.size(), errors);
    }

    private void importRecord(long datasetId, CSVRecord record, ImportDefaults defaults) {
        String question = value(record, "question");
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        String difficulty = normalizeDifficulty(value(record, "difficulty"));
        boolean enabled = parseEnabled(value(record, "enabled"));
        EvalCase evalCase = new EvalCase(
                null,
                datasetId,
                question,
                value(record, "expected_answer"),
                valueOrDefault(record, "tenant_id", defaults.tenantId()),
                valueOrDefault(record, "project_id", defaults.projectId()),
                valueOrDefault(record, "user_id", defaults.userId()),
                valueOrDefault(record, "conversation_id", defaults.conversationId()),
                split(value(record, "tags")),
                difficulty,
                enabled,
                null,
                null);
        caseService.create(evalCase, expectedKnowledge(value(record, "expected_knowledge_ids")));
    }

    private List<ExpectedKnowledge> expectedKnowledge(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<ExpectedKnowledge> result = new ArrayList<>();
        // expected_knowledge_ids 使用 | 分隔，便于在普通表格工具里维护多条标准知识。
        for (String token : value.split("\\|")) {
            String normalized = token.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                long knowledgeId = Long.parseLong(normalized);
                if (knowledgeId <= 0) {
                    throw new NumberFormatException("not positive");
                }
                result.add(new ExpectedKnowledge(
                        null,
                        null,
                        knowledgeId,
                        DEFAULT_EXPECTED_KNOWLEDGE_RELEVANCE_GRADE,
                        null));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "expected_knowledge_ids contains invalid ID: " + normalized);
            }
        }
        return result;
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\|")).stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private String normalizeDifficulty(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!DIFFICULTIES.contains(normalized)) {
            throw new IllegalArgumentException("difficulty must be EASY, MEDIUM or HARD");
        }
        return normalized;
    }

    private boolean parseEnabled(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("enabled must be true or false");
    }

    private String valueOrDefault(CSVRecord record, String name, String fallback) {
        String value = value(record, name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String value(CSVRecord record, String name) {
        if (!record.isMapped(name) || !record.isSet(name)) {
            return null;
        }
        String value = record.get(name);
        return value == null ? null : value.trim();
    }

    private Reader withoutBom(Reader reader) throws IOException {
        PushbackReader pushback = new PushbackReader(reader, 1);
        int first = pushback.read();
        if (first != -1 && first != '\uFEFF') {
            // Excel 导出的 UTF-8 CSV 可能带 BOM，导入时统一剥离。
            pushback.unread(first);
        }
        return pushback;
    }

    private String message(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    public record ImportDefaults(
            String tenantId,
            String projectId,
            String userId,
            String conversationId) {

        public static ImportDefaults empty() {
            return new ImportDefaults(null, null, null, null);
        }
    }
}
