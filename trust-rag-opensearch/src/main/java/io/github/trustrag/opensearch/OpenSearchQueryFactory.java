package io.github.trustrag.opensearch;

import io.github.trustrag.core.model.KeywordSearchRequest;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.model.TrustLevel;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenSearchQueryFactory 封装对象创建规则，避免调用方直接拼装底层结构。
 */
public final class OpenSearchQueryFactory {

    public Query create(KeywordSearchRequest request) {
        List<Query> filters = new ArrayList<>();
        filters.add(terms(
                "status",
                request.statuses().stream().map(Enum::name).toList()));
        filters.add(terms(
                "trust_level",
                request.trustLevels().stream().map(Enum::name).toList()));
        filters.add(visibleScopes(request));

        Query textQuery = new Query.Builder()
                .multiMatch(multiMatch -> multiMatch
                        .query(request.query())
                        .fields("title^3", "claim^2", "tags^2", "content"))
                .build();
        return new Query.Builder()
                .bool(bool -> bool.must(textQuery).filter(filters))
                .build();
    }

    private Query visibleScopes(KeywordSearchRequest request) {
        List<Query> visible = new ArrayList<>();
        if (request.trustLevels().stream().anyMatch(level -> level != TrustLevel.LOW)
                && includes(request, ScopeType.GLOBAL)) {
            List<String> trusted = request.trustLevels().stream()
                    .filter(level -> level != TrustLevel.LOW)
                    .map(Enum::name)
                    .toList();
            visible.add(and(
                    term("scope_type", ScopeType.GLOBAL.name()),
                    terms("trust_level", trusted)));
        }
        if (request.trustLevels().contains(TrustLevel.LOW)
                && request.allowGlobalCandidate()
                && includes(request, ScopeType.GLOBAL_CANDIDATE)) {
            visible.add(and(
                    term("scope_type", ScopeType.GLOBAL_CANDIDATE.name()),
                    term("trust_level", TrustLevel.LOW.name())));
        }
        addOwnedScope(visible, request, ScopeType.TENANT, "tenant_id", request.scope().tenantId());
        addOwnedScope(visible, request, ScopeType.PROJECT, "project_id", request.scope().projectId());
        addOwnedScope(visible, request, ScopeType.USER, "user_id", request.scope().userId());
        addOwnedScope(
                visible, request, ScopeType.CONVERSATION,
                "conversation_id", request.scope().conversationId());
        if (visible.isEmpty()) {
            return new Query.Builder().matchNone(value -> value).build();
        }
        return new Query.Builder()
                .bool(bool -> bool.should(visible).minimumShouldMatch("1"))
                .build();
    }

    private void addOwnedScope(
            List<Query> visible,
            KeywordSearchRequest request,
            ScopeType scopeType,
            String ownerField,
            String ownerId) {
        if (includes(request, scopeType) && ownerId != null && !ownerId.isBlank()) {
            visible.add(and(
                    term("scope_type", scopeType.name()),
                    term(ownerField, ownerId)));
        }
    }

    private Query and(Query... queries) {
        return new Query.Builder().bool(bool -> bool.filter(List.of(queries))).build();
    }

    private Query term(String field, String value) {
        return new Query.Builder()
                .term(term -> term.field(field).value(FieldValue.of(value)))
                .build();
    }

    private Query terms(String field, List<String> values) {
        List<FieldValue> fieldValues = values.stream().map(FieldValue::of).toList();
        return new Query.Builder()
                .terms(terms -> terms
                        .field(field)
                        .terms(value -> value.value(fieldValues)))
                .build();
    }

    private boolean includes(KeywordSearchRequest request, ScopeType scopeType) {
        return request.scopeTypes().isEmpty() || request.scopeTypes().contains(scopeType);
    }
}
