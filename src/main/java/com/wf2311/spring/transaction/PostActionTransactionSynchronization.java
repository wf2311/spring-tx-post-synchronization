package com.wf2311.spring.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.transaction.support.TransactionSynchronization;

/**
 * 后置的数据库事务同步器
 *
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/6 15:55.
 */
@Slf4j
public class PostActionTransactionSynchronization implements TransactionSynchronization {
    private PostActionTransactionSynchronizationHandler handler;

    public PostActionTransactionSynchronization(PostActionTransactionSynchronizationHandler handler) {
        this.handler = handler;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void afterCompletion(int status) {
        handler.doAfterAfterCompletion(status);
    }
}
