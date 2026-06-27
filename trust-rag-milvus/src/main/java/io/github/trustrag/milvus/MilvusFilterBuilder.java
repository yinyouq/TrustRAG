package io.github.trustrag.milvus;

import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorSearchRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MilvusFilterBuilder 负责按领域规则构建请求、查询或输出内容。
 */
public final class MilvusFilterBuilder {

    public MilvusFilter build(VectorSearchRequest request) {
        Map<String, Object> values = new HashMap<>();
        values.put("statuses", request.statuses().stream().map(Enum::name).toList());
        values.put("trustLevels", request.trustLevels().stream().map(Enum::name).toList());

        List<String> visibleScopes = new ArrayList<>();
        if (request.trustLevels().stream().anyMatch(level -> level != TrustLevel.LOW)
                && includes(request, ScopeType.GLOBAL)) {
            values.put(
                    "trustedGlobalLevels",
                    request.trustLevels().stream()
                            .filter(level -> level != TrustLevel.LOW)
                            .map(Enum::name)
                            .toList());
            visibleScopes.add(
                    "(scope_type == \"GLOBAL\" and trust_level in {trustedGlobalLevels})");
        }
        if (request.trustLevels().contains(TrustLevel.LOW)
                && request.allowGlobalCandidate()
                && includes(request, ScopeType.GLOBAL_CANDIDATE)) {
            visibleScopes.add(
                    "(scope_type == \"GLOBAL_CANDIDATE\" and trust_level == \"LOW\")");
        }
        ScopeContext scope = request.scope();
        addScope(request, visibleScopes, values, ScopeType.TENANT, "tenant_id", "tenantId", scope.tenantId());
        addScope(request, visibleScopes, values, ScopeType.PROJECT, "project_id", "projectId", scope.projectId());
        addScope(request, visibleScopes, values, ScopeType.USER, "user_id", "userId", scope.userId());
        addScope(
                request, visibleScopes, values, ScopeType.CONVERSATION,
                "conversation_id", "conversationId", scope.conversationId());

        if (visibleScopes.isEmpty()) {
            return new MilvusFilter("false", values);
        }
        String expression = "status in {statuses} and trust_level in {trustLevels} and ("
                + String.join(" or ", visibleScopes) + ")";
        return new MilvusFilter(expression, values);
    }

    private void addScope(
            VectorSearchRequest request,
            List<String> expressions,
            Map<String, Object> values,
            ScopeType scopeType,
            String field,
            String parameter,
            String ownerId) {
        if (includes(request, scopeType) && ownerId != null && !ownerId.isBlank()) {
            expressions.add("(scope_type == \"" + scopeType.name()
                    + "\" and " + field + " == {" + parameter + "})");
            values.put(parameter, ownerId);
        }
    }

    private boolean includes(VectorSearchRequest request, ScopeType scopeType) {
        return request.scopeTypes().isEmpty() || request.scopeTypes().contains(scopeType);
    }
}
