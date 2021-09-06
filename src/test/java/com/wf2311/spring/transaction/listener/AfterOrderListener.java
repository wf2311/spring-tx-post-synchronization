package com.wf2311.spring.transaction.listener;

import com.wf2311.spring.transaction.model.Order;
import com.wf2311.spring.transaction.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Optional;

/**
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/6 14:15.
 */
@Component
@Slf4j
public class AfterOrderListener {
    @Resource
    private OrderRepository orderRepository;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cacheSycResult(AfterOrderEvent event) {
        Order order = (Order) event.getSource();
        log.info("接收到订单数据：{}", order);
        Optional<Order> find = orderRepository.findById(order.getId());
        if (find.isPresent()) {
            log.info("查询到订单信息：{}", find.get());
        } else {
            log.warn("未查询到id={}的订单信息", order.getId());
        }

    }
}
