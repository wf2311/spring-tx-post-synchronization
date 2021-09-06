package com.wf2311.spring.transaction;

import com.wf2311.spring.transaction.model.Order;
import com.wf2311.spring.transaction.repository.OrderRepository;
import com.wf2311.spring.transaction.service.SaveOrderService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static com.wf2311.spring.transaction.service.impl.SaveOrderServiceImpl.FAIL;
import static com.wf2311.spring.transaction.service.impl.SaveOrderServiceImpl.SUCCESS;


/**
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/3 12:37.
 */
@Slf4j
public class TransactionTests extends AbstractTest {
    @Resource
    private OrderRepository orderRepository;
    @Resource
    private SaveOrderService saveOrderService;

    private long id = 336L;

    @Test
    @Rollback(value = false)
    public void test1() {

        System.out.println(TransactionSynchronizationManager.isActualTransactionActive());

        Order order = new Order();
        order.setId(id);
        order.setCreatedOn(LocalDateTime.now());
        order.setPrice(10f);
        orderRepository.save(order);
    }

    @SneakyThrows
    @Test
    @Rollback(value = false)
    public void test2() {
        int limit = 1;
        int request = 100;
        CountDownLatch latch = new CountDownLatch(request);
        CyclicBarrier barrier = new CyclicBarrier(request);

        for (int i = 0; i < request; i++) {
            new Thread(() -> {
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
                saveOrderService.saveLimit(limit);
                latch.countDown();
            }).start();
        }
        latch.await();
        log.info("下单成功数量：" + SUCCESS.sum() + "\t下单失败数量：" + FAIL.sum());
    }


    @SneakyThrows
    @Test
    @Rollback(value = false)
    public void testEvent() {
        saveOrderService.save(1);
        TimeUnit.SECONDS.sleep(2);
    }

}
