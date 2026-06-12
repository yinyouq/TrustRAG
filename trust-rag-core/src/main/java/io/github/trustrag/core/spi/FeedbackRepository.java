package io.github.trustrag.core.spi;

import io.github.trustrag.core.model.RagFeedbackRequest;

public interface FeedbackRepository {

    long save(RagFeedbackRequest request);
}
