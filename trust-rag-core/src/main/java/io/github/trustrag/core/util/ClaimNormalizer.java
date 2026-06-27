package io.github.trustrag.core.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * ClaimNormalizer 提供领域内部复用的工具方法，集中处理规范化和一致性逻辑。
 */
public final class ClaimNormalizer {

    private ClaimNormalizer() {
    }

    public static String normalize(String claim) {
        if (claim == null) {
            return "";
        }
        String normalized = Normalizer.normalize(claim, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }

    public static String hash(String claim) {
        return Hashing.sha256(normalize(claim));
    }
}
