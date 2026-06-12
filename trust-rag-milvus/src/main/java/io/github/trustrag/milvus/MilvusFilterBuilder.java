package io.github.trustrag.milvus;

import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.TrustLevel;
import io.github.trustrag.core.model.VectorSearchRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MilvusFilterBuilder {

    public MilvusFilter build(VectorSearchRequest request) {
        Map<String, Object> values = new HashMap<>();
        values.put("statuses", request.statuses().stream().map(Enum::name).toList());
        values.put("trustLevels", request.trustLevels().stream().map(Enum::name).toList());

        List<String> visibleScopes = new ArrayList<>();
        if (!request.trustLevels().contains(TrustLevel.LOW)) {
            visibleScopes.add("scope_type == \"GLOBAL\"");
        }
        ScopeContext scope = request.scope();
        addScope(visibleScopes, values, "TENANT", "tenant_id", "tenantId", scope.tenantId());
        addScope(visibleScopes, values, "PROJECT", "project_id", "projectId", scope.projectId());
        addScope(visibleScopes, values, "USER", "user_id", "userId", scope.userId());
        addScope(visibleScopes, values, "CONVERSATION", "conversation_id", "conversationId", scope.conversationId());

        if (visibleScopes.isEmpty()) {
            return new MilvusFilter("false", values);
        }
        String expression = "status in {statuses} and trust_level in {trustLevels} and ("
                + String.join(" or ", visibleScopes) + ")";
        return new MilvusFilter(expression, values);
    }

    private void addScope(
            List<String> expressions,
            Map<String, Object> values,
            String scopeType,
            String field,
            String parameter,
            String ownerId) {
        if (ownerId != null && !ownerId.isBlank()) {
            expressions.add("(scope_type == \"" + scopeType + "\" and " + field + " == {" + parameter + "})");
            values.put(parameter, ownerId);
        }
    }
}
