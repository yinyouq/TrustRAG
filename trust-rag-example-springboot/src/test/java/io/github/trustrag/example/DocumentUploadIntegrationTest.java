package io.github.trustrag.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.document.DocumentImportWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trust_rag_documents;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "trust-rag.document.storage-path=target/test-documents",
        "trust-rag.document.keep-source-files=false",
        "trust-rag.document.initial-delay-ms=600000"
})
/**
 * 验证 DocumentUploadIntegration 的端到端集成流程，确保多个模块协作符合预期。
 */
@AutoConfigureMockMvc
class DocumentUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentImportWorker worker;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void uploadsParsesAndImportsMarkdownAsKnowledge() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guide.md",
                "text/markdown",
                "# Overview\nTrustRAG document upload works."
                        .getBytes(StandardCharsets.UTF_8));

        String response = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("title", "Upload Guide")
                        .param("sourceUrl", "https://example.test/guide")
                        .param("sourceType", "official_doc"))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode submitted = objectMapper.readTree(response);
        String taskId = submitted.path("taskId").asText();

        assertThat(worker.runBatch()).isEqualTo(1);

        String taskResponse = mockMvc.perform(get("/api/documents/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(taskResponse).path("status").asText())
                .isEqualTo("COMPLETED");

        Map<String, Object> knowledge = jdbc.queryForMap("""
                SELECT source_title, source_url, section_path, document_id, chunk_index
                FROM knowledge_item
                WHERE document_id=?
                """, taskId);
        assertThat(knowledge.get("source_title")).isEqualTo("Upload Guide");
        assertThat(knowledge.get("source_url")).isEqualTo("https://example.test/guide");
        assertThat(knowledge.get("section_path")).isEqualTo("Overview");
        assertThat(knowledge.get("document_id")).isEqualTo(taskId);
        assertThat(knowledge.get("chunk_index")).isEqualTo(0);
    }
}
