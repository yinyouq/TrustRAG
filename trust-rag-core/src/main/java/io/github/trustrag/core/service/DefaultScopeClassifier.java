package io.github.trustrag.core.service;

import io.github.trustrag.core.model.ScopeContext;
import io.github.trustrag.core.model.ScopeType;
import io.github.trustrag.core.spi.ScopeClassifier;

import java.util.Locale;

public final class DefaultScopeClassifier implements ScopeClassifier {

    @Override
    public ScopeType classify(String content, ScopeContext context) {
        String value = content == null ? "" : content.toLowerCase(Locale.ROOT);
        if (containsAny(value, "本次对话", "这次会话", "临时", "当前日志") && hasText(context.conversationId())) {
            return ScopeType.CONVERSATION;
        }
        if (containsAny(value, "本项目", "当前项目", "项目内") && hasText(context.projectId())) {
            return ScopeType.PROJECT;
        }
        if (containsAny(value, "我偏好", "我的", "个人") && hasText(context.userId())) {
            return ScopeType.USER;
        }
        if (containsAny(value, "公司内部", "本公司", "本租户", "内部流程") && hasText(context.tenantId())) {
            return ScopeType.TENANT;
        }
        return ScopeType.GLOBAL_CANDIDATE;
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
