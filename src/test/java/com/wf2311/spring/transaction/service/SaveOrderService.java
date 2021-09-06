package com.wf2311.spring.transaction.service;

import com.wf2311.spring.transaction.model.Order;

/**
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/6 17:28.
 */
public interface SaveOrderService {

    Order save(int sleep);

    void saveLimit(int limit);

}
