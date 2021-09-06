package com.wf2311.spring.transaction;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/6 10:54.
 */
@Configuration
public class TransactionConfiguration {

    @Bean
    public PostActionTransactionSynchronizationHandler postDataSourceTransactionHandler() {
        return new PostActionTransactionSynchronizationHandler();
    }

    @Bean
    public PostActionTransactionSynchronization postActionTransactionSynchronization(PostActionTransactionSynchronizationHandler handler) {
        PostActionTransactionSynchronization synchronization = new PostActionTransactionSynchronization(handler);
        handler.setSynchronization(synchronization);
        return synchronization;
    }

}
