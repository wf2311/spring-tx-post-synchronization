package com.wf2311.spring.transaction.listener;

import com.wf2311.spring.transaction.model.Order;
import org.springframework.context.ApplicationEvent;

/**
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/6 14:20.
 */
public class AfterOrderEvent extends ApplicationEvent {
    /**
     * Create a new {@code ApplicationEvent}.
     *
     * @param source the object on which the event initially occurred or with
     *               which the event is associated (never {@code null})
     */
    public AfterOrderEvent(Order source) {
        super(source);
    }
}
