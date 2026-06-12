package io.github.trustrag.example;

import io.github.trustrag.core.model.KnowledgeItem;
import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.TokenUsage;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorHit;
import io.github.trustrag.core.model.VectorSearchRequest;
import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.service.KnowledgeVisibilityPolicy;
import io.github.trustrag.core.spi.EmbeddingClient;
import io.github.trustrag.core.spi.KnowledgeVectorStore;
import io.github.trustrag.core.spi.LlmClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration(proxyBeanMethods = false)
@Profile({"demo", "milvus"})
public class LocalModelConfiguration {

    @Bean
    EmbeddingClient localEmbeddingClient() {
        return new DeterministicEmbeddingClient(64);
    }

    @Bean
    LlmClient localLlmClient() {
        return prompt -> {
            int start = prompt.indexOf("<knowledge>");
            int end = prompt.indexOf("</knowledge>");
            String context = start >= 0 && end > start
                    ? prompt.substring(start + "<knowledge>".length(), end).trim()
                    : "";
            String answer = context.isBlank()
                    ? "资料不足，无法确定。"
                    : "根据已检索到的知识：\n" + context;
            return new LlmResponse(answer, context.isBlank() ? 0.1 : 0.85, new TokenUsage(0, 0));
        };
    }

    @Bean
    @Profile("demo")
    KnowledgeVectorStore inMemoryKnowledgeVectorStore(KnowledgeVisibilityPolicy visibilityPolicy) {
        return new InMemoryKnowledgeVectorStore(visibilityPolicy);
    }

    static final class DeterministicEmbeddingClient implements EmbeddingClient {
        private final int dimension;

        DeterministicEmbeddingClient(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public List<Float> embed(String text) {
            byte[] seed;
            try {
                seed = MessageDigest.getInstance("SHA-256")
                        .digest(text.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
            float[] values = new float[dimension];
            for (int index = 0; index < text.length(); index++) {
                int bucket = Math.floorMod(text.charAt(index) * 31 + index, dimension);
                values[bucket] += 1.0f + (seed[index % seed.length] & 0x0F) / 32.0f;
            }
            double norm = 0.0;
            for (float value : values) {
                norm += value * value;
            }
            norm = Math.sqrt(norm);
            List<Float> result = new ArrayList<>(dimension);
            for (float value : values) {
                result.add(norm == 0.0 ? 0.0f : (float) (value / norm));
            }
            return result;
        }

        @Override
        public String modelName() {
            return "deterministic-demo-embedding";
        }

        @Override
        public int dimension() {
            return dimension;
        }
    }

    static final class InMemoryKnowledgeVectorStore implements KnowledgeVectorStore {
        private final Map<Long, Entry> entries = new ConcurrentHashMap<>();
        private final KnowledgeVisibilityPolicy visibilityPolicy;

        InMemoryKnowledgeVectorStore(KnowledgeVisibilityPolicy visibilityPolicy) {
            this.visibilityPolicy = visibilityPolicy;
        }

        @Override
        public void initialize() {
        }

        @Override
        public void upsert(KnowledgeItem knowledge, List<Float> vector) {
            entries.put(knowledge.id(), new Entry(knowledge, List.copyOf(vector)));
        }

        @Override
        public void delete(long knowledgeId) {
            entries.remove(knowledgeId);
        }

        @Override
        public List<VectorHit> search(VectorSearchRequest request) {
            ScopeContext scope = request.scope();
            return entries.values().stream()
                    .filter(entry -> request.statuses().contains(entry.knowledge().status()))
                    .filter(entry -> request.trustLevels().contains(entry.knowledge().trustLevel()))
                    .filter(entry -> entry.knowledge().trustLevel() != TrustLevel.LOW
                            || entry.knowledge().scopeType() != io.github.trustrag.core.model.ScopeType.GLOBAL)
                    .filter(entry -> visibilityPolicy.isVisible(entry.knowledge(), scope))
                    .map(entry -> new VectorHit(entry.knowledge().id(), cosine(request.vector(), entry.vector())))
                    .filter(hit -> hit.vectorScore() >= request.minScore())
                    .sorted(Comparator.comparingDouble(VectorHit::vectorScore).reversed())
                    .limit(request.topK())
                    .toList();
        }

        private double cosine(List<Float> left, List<Float> right) {
            double dot = 0.0;
            double leftNorm = 0.0;
            double rightNorm = 0.0;
            for (int index = 0; index < left.size(); index++) {
                dot += left.get(index) * right.get(index);
                leftNorm += left.get(index) * left.get(index);
                rightNorm += right.get(index) * right.get(index);
            }
            return leftNorm == 0.0 || rightNorm == 0.0 ? 0.0 : dot / Math.sqrt(leftNorm * rightNorm);
        }

        private record Entry(KnowledgeItem knowledge, List<Float> vector) {
        }
    }
}
