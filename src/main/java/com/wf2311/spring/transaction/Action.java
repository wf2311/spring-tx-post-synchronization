package com.wf2311.spring.transaction;

/**
 * 函数式接口：要执行方法
 * @author <a href="mailto:wf2311@163.com">wf2311</a>
 * @since 2021/9/6 11:25.
 */
@FunctionalInterface
public interface Action {

    /**
     * 执行方法
     */
    void execute();

}
