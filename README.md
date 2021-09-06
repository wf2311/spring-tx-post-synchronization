# spring-transaction-synchronization

## 使用场景
在Spring事务中发送MQ或进行加锁操作，在事务还未结束时，MQ就已经发送出去或锁已经被释放掉，导致数据不一致性问题：

## 问题场景
### 场景一
```java
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order save(Order order) {
        orderRepository.save(order);
        
        //发送mq
        orderSender.send(order);
        
        //do something
        
        return order;
    }
```
上述代码中，在保存订单后立即发送MQ，但此时事务还没提交，如果`do something`的后续逻辑耗时较久，就会有几率，MQ消费者在接收到消息后，生产者的事务还未提交，假设消费者此时要到数据库反查order，就会查不到；

### 场景二
```java
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void seckill() {
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
            lock.unlock();
        }
    }
```

上述代码中，虽然解锁操作`lock.unlock()`是放在在finally里面执行的，但实际上在使用事务时，经过AOP代理后，`lock.unlock()`会早于事务提交执行，因此还是存在并发问题。

## 解决方案
要解决上述的问题，本质上就是要让事务或解锁操作，在事务提交后再执行，可以使用手动提交事务方式，直接后置这些操作，但此种实现起来对业务代码侵入性较大，不太优雅。

这里我将介绍另一种方式——通过扩展Spring事务的同步机制来解决此问题：

### Spring事务的同步机制
Spring事务的同步机制的具体原理这里就不做详细介绍了，具体可以参考[https://blog.csdn.net/f641385712/article/details/91538445](https://blog.csdn.net/f641385712/article/details/91538445)这篇文章

简单来说，我们可以通过自定义TransactionSynchronization，然后通过在事务方法中使用`TransactionSynchronizationManager.registerSynchronization(synchronization)`方法来注册事务同步器，在事务完成前后的进行相关逻辑操作；

接口`TransactionSynchronization`的定义如下：

```java
public interface TransactionSynchronization extends Ordered, Flushable {

	/** Completion status in case of proper commit. */
	int STATUS_COMMITTED = 0;

	/** Completion status in case of proper rollback. */
	int STATUS_ROLLED_BACK = 1;

	/** Completion status in case of heuristic mixed completion or system errors. */
	int STATUS_UNKNOWN = 2;

	
	@Override
	default int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}


	default void suspend() {
	}
	
	default void resume() {
	}
	
	@Override
	default void flush() {
	}
	
	default void beforeCommit(boolean readOnly) {
	}
	
	default void beforeCompletion() {
	}
	
	default void afterCommit() {
	}

	default void afterCompletion(int status) {
	}
}
```
例如，对之前场景一的代码进行改写:
```java

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order save(Order order) {
        orderRepository.save(order);

        //发送mq
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                orderSender.send(order);
            }
        });

        //do something

        return order;
    }
```

我们注册了一个自定义的事务同步器，其中：
```java
public void afterCommit() {
    orderSender.send(order);
}
```
即表示在事务完成提交后进行MQ消息发送

### 进一步封装
了解了Spring的事务同步机制，我们就可以对此进行一下封装

- 函数式接口：要执行方法
```java
@FunctionalInterface
public interface Action {

    /**
     * 执行方法
     */
    void execute();

}
```

- 枚举类：定义需要在事务处于何种状态时进行操作
```java
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
```

- 事务状态操作封装类
```java
public class ActionWrapper {
    /**
     * Map:<事务状态，操作> 
     */
    private Map<ActionExecuteState, List<Action>> actionMap;

    public ActionWrapper(@NotNull Map<ActionExecuteState, List<Action>> actionMap) {
        assert actionMap != null;
        this.actionMap = actionMap;
    }

    public Map<ActionExecuteState, List<Action>> getActionMap() {
        return actionMap;
    }
}
```

- 后置的数据库事务同步处理器
```java
@Slf4j
public class PostActionTransactionSynchronizationHandler {
    /**
     * Supplier:用于判断当前是否出去活动事务中，默认为 {@code TransactionSynchronizationManager::isActualTransactionActive}
     */
    private final Supplier<Boolean> transactionActiveSupplier;

    /**
     * 通过ThreadLocal缓存写操作方法
     */
    private final ThreadLocal<ActionWrapper> cache = ThreadLocal.withInitial(() -> null);
    /**
     * 缓存当前线程中
     */
    private final ThreadLocal<Boolean> initSign = ThreadLocal.withInitial(() -> null);

    private PostActionTransactionSynchronization synchronization;

    public PostActionTransactionSynchronizationHandler(Supplier<Boolean> transactionActiveSupplier) {
        this.transactionActiveSupplier = transactionActiveSupplier;
    }

    public PostActionTransactionSynchronizationHandler() {
        this(TransactionSynchronizationManager::isActualTransactionActive);
    }

    public void setSynchronization(PostActionTransactionSynchronization synchronization) {
        this.synchronization = synchronization;
    }

    /**
     * 判断当前是否出去活动事务中
     *
     * @return
     */
    public boolean isActualTransactionActive() {
        return transactionActiveSupplier.get();
    }

    protected List<Action> getActions(ActionExecuteState state) {
        ActionWrapper wrapper = cache.get();
        if (wrapper == null || state == null) {
            return Collections.emptyList();
        }
        Map<ActionExecuteState, List<Action>> map = wrapper.getActionMap();
        if (map == null) {
            return Collections.emptyList();
        }
        return map.get(state);
    }


    /**
     * 添加一个事务后操作方法
     *
     * @param action 操作方法
     * @param state  action要运行在的事务状态
     */
    public synchronized void addAction(Action action, ActionExecuteState state) {
        registerSynchronizationIfNeed();
        ActionWrapper wrapper = cache.get();
        if (wrapper == null) {
            wrapper = new ActionWrapper(new HashMap<>(4));
            cache.set(wrapper);
        }
        Map<ActionExecuteState, List<Action>> map = wrapper.getActionMap();
        List<Action> actions = map.computeIfAbsent(state, s -> new ArrayList<>());
        actions.add(action);
    }

    private void registerSynchronizationIfNeed() {
        Boolean flag = initSign.get();
        if (flag == null || !flag) {
            TransactionSynchronizationManager.registerSynchronization(synchronization);
            initSign.set(true);
        }
    }

    /**
     * 清理缓存
     */
    public void clear() {
        cache.remove();
    }

    public void doAfterAfterCompletion(int stateCode) {
        try {
            ActionExecuteState state = ActionExecuteState.getByState(stateCode);
            if (state != null) {
                executeActionByState(state);
            }
            executeActionByState(ActionExecuteState.WHEN_ALL);
        } finally {
            this.clear();
        }
    }

    private void executeActionByState(ActionExecuteState state) {
        if (state == null) {
            return;
        }
        try {
            List<Action> actions = getActions(state);
            if (CollectionUtils.isEmpty(actions)) {
                return;
            }
            if (log.isInfoEnabled()) {
                log.info("begin execute action with state={}", state);
            }
            for (Action action : actions) {
                if (log.isDebugEnabled()) {
                    log.debug("execute {}", action.toString());
                }
                action.execute();
            }
        } catch (Exception e) {
            log.error("execute actions after transaction completion fail,state=" + state, e);
        }
    }
}
```
- 后置的数据库事务同步器
```java
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
```

使用方式：
- 定义bean
```java
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

```

代码改写

- 场景一
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public Order save(Order order) {
    orderRepository.save(order);
    
    //发送mq
    //注释orderSender.send(order); 
    //ActionExecuteState.WHEN_COMMITTED 表示只有当事务成功提交时才执行lock.unlock()
    postActionTransactionSynchronizationHandler.addAction(lock::unlock, ActionExecuteState.WHEN_COMMITTED)
    
    //do something
    
    return order;
}
```

- 场景二

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
@Override
public void seckill() {
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
        //注释lock.unlock() 
        //ActionExecuteState.WHEN_ALL 表示只要事务结束不管是回滚还是提交都会执行lock.unlock()
        postActionTransactionSynchronizationHandler.addAction(lock::unlock, ActionExecuteState.WHEN_ALL)
    }
}
```

可以参考本项目的 `src/test/java`目录查看具体测试用例和用法 







