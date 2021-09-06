package com.wf2311.spring.transaction;

import com.google.common.base.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 后置的数据库事务同步处理器，使用场景：
 * 在事务代码中先定义后要执行的代码，但在事务完成后再执行
 *
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/6 14:46.
 */
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

