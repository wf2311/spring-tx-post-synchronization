package com.wf2311.spring.transaction;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.Objects;

/**
 * 枚举类：定义需要在事务处于何种状态时进行操作
 *
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/6 16:11.
 */
@Getter
@AllArgsConstructor
public enum ActionExecuteState {
    /**
     * 事务提交时执行Action
     */
    WHEN_COMMITTED(TransactionSynchronization.STATUS_COMMITTED),
    /**
     * 事务回滚时执行Action
     */
    WHEN_ROLL_BACK(TransactionSynchronization.STATUS_ROLLED_BACK),
    /**
     * 事务状态未知时执行Action
     */
    WHEN_UNKNOWN(TransactionSynchronization.STATUS_UNKNOWN),
    /**
     * 不管事务状态为何值下都会执行Action
     */
    WHEN_ALL(null);
    private Integer state;

    public static ActionExecuteState getByState(Integer state) {
        if (state == null) {
            return WHEN_ALL;
        }
        for (ActionExecuteState e : ActionExecuteState.values()) {
            if (Objects.equals(e.state, state)) {
                return e;
            }
        }
        return null;
    }
}
