package io.github.trustrag.core.util;

import java.text.Normalizer;
import java.util.Locale;

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
