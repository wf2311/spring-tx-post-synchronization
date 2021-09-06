package com.wf2311.spring.transaction.service.impl;

import com.wf2311.spring.transaction.ActionExecuteState;
import com.wf2311.spring.transaction.PostActionTransactionSynchronizationHandler;
import com.wf2311.spring.transaction.listener.AfterOrderEvent;
import com.wf2311.spring.transaction.model.Order;
import com.wf2311.spring.transaction.repository.OrderRepository;
import com.wf2311.spring.transaction.service.SaveOrderService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/6 17:28.
 */
@Service
@Slf4j
public class SaveOrderServiceImpl implements SaveOrderService {

    private final ReentrantLock lock = new ReentrantLock();

    @Resource
    private OrderRepository orderRepository;

    @Resource
    private PostActionTransactionSynchronizationHandler postActionTransactionSynchronizationHandler;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;


    public static final LongAdder SUCCESS = new LongAdder();
    public static final LongAdder FAIL = new LongAdder();


    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order save(int sleep) {
        Order order = new Order();
        order.setPrice(123L);
        order.setCreatedOn(LocalDateTime.now());
        orderRepository.save(order);

        postActionTransactionSynchronizationHandler
                .addAction(() -> applicationEventPublisher.publishEvent(new AfterOrderEvent(order)), ActionExecuteState.WHEN_COMMITTED);

        if (sleep > 0) {
            log.info("wait {} seconds ... ", sleep);
            TimeUnit.SECONDS.sleep(sleep);
        }
        return order;
    }


    @SneakyThrows
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void saveLimit(int limit) {
        lock.lock();
        try {
            Integer exists = orderRepository.countAllBy();
            log.info("当前库存:" + (limit - exists));
            if (exists >= limit) {
                log.warn("下单失败了");
                FAIL.increment();
                return;
            }

            Order order = new Order();
            order.setPrice(123L);
            order.setCreatedOn(LocalDateTime.now());
            orderRepository.save(order);
            log.info(" 下单成功了");
            SUCCESS.increment();
        } finally {
            postActionTransactionSynchronizationHandler.addAction(lock::unlock, ActionExecuteState.WHEN_ALL);
        }
    }
}
