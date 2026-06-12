package io.github.trustrag.core.spi;

import java.util.function.Supplier;

public interface TransactionRunner {

    <T> T required(Supplier<T> action);

    default void required(Runnable action) {
        required(() -> {
            action.run();
            return null;
        });
    }

    static TransactionRunner direct() {
        return new TransactionRunner() {
            @Override
            public <T> T required(Supplier<T> action) {
                return action.get();
            }
        };
    }
}
