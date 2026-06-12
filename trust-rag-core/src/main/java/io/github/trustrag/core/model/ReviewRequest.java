package io.github.trustrag.core.model;

public record ReviewRequest(
        String reviewerId,
        String comment,
        String modifiedTitle,
        String modifiedContent) {
}
