package com.kfyty.aop;

import com.kfyty.aop.proxy.AspectMethodInterceptorProxy;
import com.kfyty.aop.aspectj.creator.AspectJAdvisorCreator;
import com.kfyty.support.proxy.factory.DynamicProxyFactory;
import com.kfyty.support.utils.AopUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.junit.Assert;
import org.junit.Test;

import java.lang.annotation.Retention;
import java.util.List;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 描述: Aop 单独使用测试
 *
 * @author kfyty725
 * @date 2021/8/2 10:56
 * @email kfyty725@hotmail.com
 */
@Slf4j
public class AopTest {

    @Test
    public void aopTest() {
        LogAspect logAspect = new LogAspect();
        Service proxy = DynamicProxyFactory.create().createProxy(new ServiceImpl());
        List<Advisor> advisors = new AspectJAdvisorCreator().createAdvisor(e -> logAspect, LogAspect.class);
        AopUtil.addProxyInterceptorPoint(proxy, new AspectMethodInterceptorProxy(advisors));
        log.info("do service return value: {}", proxy.doService(1));
    }
}

@Retention(RUNTIME)
@interface Log {
}

interface Service {
    int doService(int index);
}

@Slf4j
class ServiceImpl implements Service {
    @Log
    @Override
    public int doService(int index) {
        log.info("invoke -> target, and param: {}", index);
        return ++index;
    }
}

@Slf4j
@Aspect
class LogAspect {
    private int result = 1;

    @Pointcut("@annotation(com.kfyty.aop.Log)")
    public void pointcut() {

    }

    @Before(value = "@annotation(logA) && args(a,..)", argNames = "joinPoint,logA,a")
    public void before(JoinPoint joinPoint, Log logA, int a) throws Throwable {
        log.debug("invoke before");
        Assert.assertSame(2, result);
    }

    @Around(value = "@annotation(com.kfyty.aop.Log) && args(a,..)", argNames = "joinPoint,a")
    public Object around(ProceedingJoinPoint joinPoint, int a) throws Throwable {
        log.debug("invoke around start...");
        Assert.assertSame(a, result++);
        Object retValue = joinPoint.proceed();
        log.debug("invoke around end, and return value: {}", retValue);
        Assert.assertEquals(retValue, result);
        return retValue;
    }

    @AfterReturning(value = "execution(* com.kfyty.aop.*.*(..)) && args(index,..)", returning = "retValue", argNames = "joinPoint,index,retValue")
    public void afterReturning(JoinPoint joinPoint, int index, int retValue) throws Throwable {
        log.debug("invoke after returning, and return value: {}", retValue);
        Assert.assertSame(index, 1);
        Assert.assertSame(retValue, result);
    }

    @After(value = "pointcut()")
    public void after() throws Throwable {
        log.debug("invoke after");
        Assert.assertSame(2, result);
    }
}
