package io.github.trustrag.starter;

import io.github.trustrag.core.spi.TransactionRunner;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * SpringTransactionRunner 承载 trust-rag-spring-boot-starter 模块中的领域逻辑或基础设施适配职责。
 */
final class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate transactionTemplate;

    SpringTransactionRunner(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T required(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }
}
