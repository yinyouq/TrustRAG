package io.github.trustrag.core.spi;

import java.util.function.Supplier;

/**
 * TransactionRunner 是核心扩展点接口，用于隔离默认实现和外部系统。
 */
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
