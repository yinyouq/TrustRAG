package io.github.trustrag.opensearch;

import java.util.ArrayList;
import java.util.List;

final class OpenSearchKeywordQuerySanitizer {

    static final int MAX_QUERY_TERMS = 128;
    private static final int MAX_TERM_CODE_POINTS = 64;

    private OpenSearchKeywordQuerySanitizer() {
    }

    static String sanitize(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        List<String> terms = new ArrayList<>(MAX_QUERY_TERMS);
        StringBuilder current = new StringBuilder();
        query.codePoints().forEach(codePoint -> {
            if (terms.size() >= MAX_QUERY_TERMS) {
                return;
            }
            if (isCjk(codePoint)) {
                flush(current, terms);
                terms.add(new String(Character.toChars(codePoint)));
                return;
            }
            if (isTokenChar(codePoint)) {
                append(current, codePoint);
                return;
            }
            flush(current, terms);
        });
        flush(current, terms);
        if (terms.isEmpty()) {
            return query.trim();
        }
        return String.join(" ", terms);
    }

    private static void append(StringBuilder current, int codePoint) {
        if (current.codePointCount(0, current.length()) < MAX_TERM_CODE_POINTS) {
            current.appendCodePoint(codePoint);
        }
    }

    private static void flush(StringBuilder current, List<String> terms) {
        if (current.isEmpty() || terms.size() >= MAX_QUERY_TERMS) {
            current.setLength(0);
            return;
        }
        terms.add(current.toString());
        current.setLength(0);
    }

    private static boolean isTokenChar(int codePoint) {
        return Character.isLetterOrDigit(codePoint)
                || codePoint == '_'
                || codePoint == '#'
                || codePoint == '+';
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO;
    }
}
