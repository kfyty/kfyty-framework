package com.kfyty.loveqq.framework.core.proxy.aop.adapter;

import com.kfyty.loveqq.framework.core.autoconfig.annotation.Order;
import com.kfyty.loveqq.framework.core.proxy.MethodInterceptorChain;
import com.kfyty.loveqq.framework.core.proxy.MethodInterceptorChainPoint;
import com.kfyty.loveqq.framework.core.proxy.MethodProxy;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;

/**
 * 描述: 暴露 MethodInvocationProceedingJoinPoint
 *
 * @author kfyty725
 * @date 2021/8/1 14:33
 * @email kfyty725@hotmail.com
 */
@RequiredArgsConstructor
@Order(Integer.MIN_VALUE)
public class ExposeInvocationInterceptorProxy implements MethodInterceptorChainPoint {
    private static final ThreadLocal<JoinPoint> CURRENT_JOIN_POINT = new ThreadLocal<>();

    private final JoinPoint joinPoint;

    public static JoinPoint currentJoinPoint() {
        JoinPoint joinPoint = CURRENT_JOIN_POINT.get();
        if (joinPoint == null) {
            throw new IllegalStateException("the join point does not exists on this thread !");
        }
        return joinPoint;
    }

    @Override
    public Object proceed(MethodProxy methodProxy, MethodInterceptorChain chain) throws Throwable {
        JoinPoint oldJoinPoint = CURRENT_JOIN_POINT.get();
        try {
            CURRENT_JOIN_POINT.set(this.joinPoint);
            return chain.proceed(methodProxy);
        } finally {
            CURRENT_JOIN_POINT.set(oldJoinPoint);
        }
    }
}
