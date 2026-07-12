package io.github.trustrag.core.service;

import io.github.trustrag.core.model.LlmResponse;
import io.github.trustrag.core.spi.LlmClient;

/**
 * 为用户纠错沉淀的候选知识生成问题式标题。
 */
public final class CorrectionTitleGenerator {

    private static final int MAX_INPUT_LENGTH = 2000;
    private static final int MAX_TITLE_LENGTH = 120;

    private final LlmClient llmClient;

    public CorrectionTitleGenerator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String generate(String originalQuestion, String correctedAnswer) {
        String fallback = fallbackTitle(originalQuestion, correctedAnswer);
        if (llmClient == null || !hasText(correctedAnswer)) {
            return fallback;
        }
        try {
            LlmResponse response = llmClient.generate(prompt(originalQuestion, correctedAnswer));
            String title = normalizeTitle(response == null ? null : response.text());
            return hasText(title) ? title : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private String prompt(String originalQuestion, String correctedAnswer) {
        return """
                你是知识库标题生成器。请根据用户原始问题和用户提供的纠错回答，生成一个能代表这段回答的用户问题，作为知识库标题。

                要求：
                - 只输出问题本身，不要解释、编号、引号或 Markdown。
                - 标题必须是自然的问题句，适合后续检索召回。
                - 优先使用原问题的语言；如果纠错回答更明确，可以补全问题中的关键限定。
                - 不要编造纠错回答中没有的事实。
                - 控制在 120 个字符以内。

                用户原始问题：
                %s

                用户纠错回答：
                %s
                """.formatted(truncate(originalQuestion), truncate(correctedAnswer));
    }

    private String fallbackTitle(String originalQuestion, String correctedAnswer) {
        String value = hasText(originalQuestion) ? originalQuestion : correctedAnswer;
        return normalizeTitle(value);
    }

    private String normalizeTitle(String value) {
        if (!hasText(value)) {
            return "";
        }
        String title = value.trim();
        title = stripCodeFence(title);
        title = stripWrappingQuotes(title);
        title = title.replaceFirst("^(?i:title|question)\\s*[:：]\\s*", "");
        title = title.replaceFirst("^(标题|问题)\\s*[:：]\\s*", "");
        title = title.replaceAll("(?m)^\\s*[-*]\\s+", "");
        title = title.replaceAll("(?m)^\\s*\\d+[.)、]\\s+", "");
        title = title.replaceAll("[\\r\\n]+", " ");
        title = title.replaceAll("\\s+", " ").trim();
        title = stripWrappingQuotes(title);
        return truncateTitle(title);
    }

    private String stripCodeFence(String value) {
        String title = value.trim();
        if (!title.startsWith("```")) {
            return title;
        }
        title = title.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "");
        title = title.replaceFirst("\\s*```$", "");
        return title.trim();
    }

    private String stripWrappingQuotes(String value) {
        String title = value.trim();
        while (title.length() >= 2 && isQuotePair(title.charAt(0), title.charAt(title.length() - 1))) {
            title = title.substring(1, title.length() - 1).trim();
        }
        return title;
    }

    private boolean isQuotePair(char first, char last) {
        return (first == '"' && last == '"')
                || (first == '\'' && last == '\'')
                || (first == '“' && last == '”')
                || (first == '‘' && last == '’')
                || (first == '《' && last == '》');
    }

    private String truncate(String value) {
        if (!hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_INPUT_LENGTH
                ? normalized
                : normalized.substring(0, MAX_INPUT_LENGTH);
    }

    private String truncateTitle(String value) {
        if (value.length() <= MAX_TITLE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TITLE_LENGTH).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
