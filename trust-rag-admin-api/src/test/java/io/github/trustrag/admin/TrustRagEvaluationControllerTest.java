package io.github.trustrag.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.evaluation.EvalCaseCsvImportResult;
import io.github.trustrag.evaluation.EvalCaseCsvImportService;
import io.github.trustrag.evaluation.EvalCaseService;
import io.github.trustrag.evaluation.EvalDataset;
import io.github.trustrag.evaluation.EvalDatasetService;
import io.github.trustrag.evaluation.EvalJudgeDetail;
import io.github.trustrag.evaluation.EvalReportService;
import io.github.trustrag.evaluation.EvalRunService;
import io.github.trustrag.evaluation.EvaluationRepository;
import io.github.trustrag.evaluation.GovernanceMetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 TrustRagEvaluationController 的关键行为、边界条件和回归场景。
 */
class TrustRagEvaluationControllerTest {

    private EvalDatasetService datasetService;
    private EvalCaseService caseService;
    private EvalCaseCsvImportService csvImportService;
    private EvaluationRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        datasetService = mock(EvalDatasetService.class);
        caseService = mock(EvalCaseService.class);
        csvImportService = mock(EvalCaseCsvImportService.class);
        repository = mock(EvaluationRepository.class);
        TrustRagEvaluationController controller = new TrustRagEvaluationController(
                datasetService,
                caseService,
                csvImportService,
                mock(EvalRunService.class),
                mock(EvalReportService.class),
                mock(GovernanceMetricService.class),
                repository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TrustRagExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper().findAndRegisterModules()))
                .build();
    }

    @Test
    void updatesDataset() throws Exception {
        when(datasetService.update(1L, "Regression", "desc", "tenant", "project", false))
                .thenReturn(new EvalDataset(
                        1L, "Regression", "desc", "tenant", "project", "alice",
                        false, Instant.EPOCH, Instant.EPOCH));

        mockMvc.perform(put("/trust-rag/admin/eval/datasets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Regression",
                                  "description":"desc",
                                  "tenantId":"tenant",
                                  "projectId":"project",
                                  "enabled":false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Regression"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void importsCsvAndReturnsRowErrors() throws Exception {
        when(csvImportService.importCsv(eq(1L), any(), any()))
                .thenReturn(new EvalCaseCsvImportResult(
                        2, 1, 1,
                        List.of(new EvalCaseCsvImportResult.RowError(3L, "invalid ID"))));
        MockMultipartFile file = new MockMultipartFile(
                "file", "cases.csv", "text/csv", "question\nhello".getBytes());

        mockMvc.perform(multipart("/trust-rag/admin/eval/cases/import-csv")
                        .file(file)
                        .param("datasetId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedRows").value(1))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(3));
    }

    @Test
    void exposesJudgeDetails() throws Exception {
        when(repository.listJudgeDetails(9L)).thenReturn(List.<EvalJudgeDetail>of());

        mockMvc.perform(get("/trust-rag/admin/eval/results/9/judge-details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void deletesCaseWithNoContentStatus() throws Exception {
        mockMvc.perform(delete("/trust-rag/admin/eval/cases/4"))
                .andExpect(status().isNoContent());

        verify(caseService).delete(4L);
    }
}
