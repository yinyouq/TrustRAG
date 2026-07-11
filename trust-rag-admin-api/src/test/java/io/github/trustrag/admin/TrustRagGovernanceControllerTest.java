package io.github.trustrag.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.KnowledgeStatus;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.spi.KnowledgeLifecycleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证知识治理 HTTP 操作会调用对应生命周期服务。
 */
class TrustRagGovernanceControllerTest {

    private KnowledgeLifecycleManager lifecycleManager;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lifecycleManager = mock(KnowledgeLifecycleManager.class);
        TrustRagGovernanceController controller = new TrustRagGovernanceController(
                null, null, null, null, lifecycleManager);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new ObjectMapper().findAndRegisterModules()))
                .build();
    }

    @Test
    void routesLifecycleActions() throws Exception {
        when(lifecycleManager.downgrade(11L, "admin", "wrong")).thenReturn(
                item(11L, TrustLevel.MEDIUM, KnowledgeStatus.MEDIUM_ENABLED));
        when(lifecycleManager.rollback(12L, "admin", "restore")).thenReturn(
                item(12L, TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED));
        when(lifecycleManager.merge(List.of(21L, 22L), 20L, "admin")).thenReturn(
                item(20L, TrustLevel.HIGH, KnowledgeStatus.HIGH_ENABLED));

        mockMvc.perform(post("/trust-rag/admin/knowledge/11/downgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"admin","reason":"wrong"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MEDIUM_ENABLED"));
        mockMvc.perform(post("/trust-rag/admin/knowledge/12/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"admin","reason":"restore"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIGH_ENABLED"));
        mockMvc.perform(post("/trust-rag/admin/knowledge/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorId":"admin",
                                  "targetKnowledgeId":20,
                                  "sourceKnowledgeIds":[21,22]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20));

        verify(lifecycleManager).downgrade(11L, "admin", "wrong");
        verify(lifecycleManager).rollback(12L, "admin", "restore");
        verify(lifecycleManager).merge(List.of(21L, 22L), 20L, "admin");
    }

    @Test
    void deletesKnowledgeThroughLifecycleManager() throws Exception {
        when(lifecycleManager.delete(31L, "admin", "wrong knowledge")).thenReturn(
                item(31L, TrustLevel.HIGH, KnowledgeStatus.REJECTED));

        mockMvc.perform(delete("/trust-rag/admin/knowledge/31")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorId":"admin","reason":"wrong knowledge"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(lifecycleManager).delete(31L, "admin", "wrong knowledge");
    }

    private KnowledgeItem item(long id, TrustLevel trustLevel, KnowledgeStatus status) {
        return new KnowledgeItem(
                id, "title-" + id, "claim-" + id, "content-" + id, null, "test",
                trustLevel, status, ScopeType.GLOBAL, null, null, null, null,
                "manual", "manual://" + id, "evidence", Long.toString(id),
                "test", 1, 1.0, 0.0, 1, "hash-" + id,
                null, null, null, Instant.EPOCH, Instant.EPOCH, null);
    }
}
