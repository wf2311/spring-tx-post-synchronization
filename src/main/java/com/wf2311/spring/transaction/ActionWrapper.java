package com.wf2311.spring.transaction;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 事务状态操作封装类
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/6 16:20.
 */
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
