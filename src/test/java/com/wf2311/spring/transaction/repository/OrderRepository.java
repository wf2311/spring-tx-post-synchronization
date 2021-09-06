package com.wf2311.spring.transaction.repository;

import com.wf2311.spring.transaction.model.Order;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/6 16:45.
 */
public interface OrderRepository extends CrudRepository<Order, Long> {

    @Transactional(propagation = Propagation.SUPPORTS)
    Integer countAllBy();

}
