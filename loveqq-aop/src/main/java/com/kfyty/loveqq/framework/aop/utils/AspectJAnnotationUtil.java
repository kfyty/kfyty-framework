package com.kfyty.loveqq.framework.aop.utils;

import com.kfyty.loveqq.framework.aop.aspectj.AbstractAspectJAdvice;
import com.kfyty.loveqq.framework.aop.aspectj.AspectJAfterReturningAdvice;
import com.kfyty.loveqq.framework.aop.aspectj.AspectJMethodAfterAdvice;
import com.kfyty.loveqq.framework.aop.aspectj.AspectJMethodAroundAdvice;
import com.kfyty.loveqq.framework.aop.aspectj.AspectJMethodBeforeAdvice;
import com.kfyty.loveqq.framework.aop.aspectj.AspectJThrowingAdvice;
import com.kfyty.loveqq.framework.core.proxy.aop.AfterReturningAdvice;
import com.kfyty.loveqq.framework.core.proxy.aop.MethodAfterAdvice;
import com.kfyty.loveqq.framework.core.proxy.aop.MethodAroundAdvice;
import com.kfyty.loveqq.framework.core.proxy.aop.MethodBeforeAdvice;
import com.kfyty.loveqq.framework.core.proxy.aop.ThrowingAdvice;
import com.kfyty.loveqq.framework.core.utils.AnnotationUtil;
import com.kfyty.loveqq.framework.core.utils.CommonUtil;
import com.kfyty.loveqq.framework.core.utils.ReflectUtil;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Before;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Objects;

/**
 * 描述: 切面注解工具
 *
 * @author kfyty725
 * @date 2021/7/29 12:06
 * @email kfyty725@hotmail.com
 */
public abstract class AspectJAnnotationUtil {
    /**
     * 支持的注解类型，该顺序也是通知顺序
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends Annotation>[] ASPECT_ANNOTATION_TYPES = new Class[]{
            Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class
    };

    public static String[] findArgNames(Method method) {
        Annotation annotation = findAspectAnnotation(method);
        if (annotation != null) {
            String argNames = ReflectUtil.invokeMethod(annotation, "argNames");
            if (CommonUtil.notEmpty(argNames)) {
                return Arrays.stream(argNames.split(",")).map(String::trim).toArray(String[]::new);
            }
        }
        return Arrays.stream(method.getParameters()).map(Parameter::getName).toArray(String[]::new);
    }

    public static Annotation findAspectAnnotation(Method method) {
        for (Class<? extends Annotation> annotationType : ASPECT_ANNOTATION_TYPES) {
            Annotation annotation = AnnotationUtil.findAnnotation(method, annotationType);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    public static int findAspectOrder(Class<?> adviceType) {
        Class<? extends Annotation> annotationType = resolveAnnotationTypeFor(adviceType);
        if (annotationType == null) {
            return 99;
        }
        int index = 0;
        for (Class<? extends Annotation> aspectType : ASPECT_ANNOTATION_TYPES) {
            if (aspectType == annotationType) {
                return index;
            }
            index++;
        }
        return 99;
    }

    public static String findAspectExpression(Method method) {
        Annotation aspectAnnotation = findAspectAnnotation(method);
        if (aspectAnnotation == null) {
            return null;
        }
        String expression = ReflectUtil.invokeMethod(aspectAnnotation, "value");
        if (CommonUtil.empty(expression)) {
            Method pointcut = ReflectUtil.getMethod(aspectAnnotation.annotationType(), "pointcut");
            if (pointcut != null) {
                expression = (String) ReflectUtil.invokeMethod(aspectAnnotation, pointcut);
            }
        }
        return CommonUtil.empty(expression) ? null : expression;
    }

    public static AbstractAspectJAdvice resolveAspectFor(Class<?> annotationType) {
        Objects.requireNonNull(annotationType);
        if (annotationType.equals(Before.class)) {
            return new AspectJMethodBeforeAdvice();
        }
        if (annotationType.equals(Around.class)) {
            return new AspectJMethodAroundAdvice();
        }
        if (annotationType.equals(AfterReturning.class)) {
            return new AspectJAfterReturningAdvice();
        }
        if (annotationType.equals(AfterThrowing.class)) {
            return new AspectJThrowingAdvice();
        }
        if (annotationType.equals(After.class)) {
            return new AspectJMethodAfterAdvice();
        }
        throw new IllegalArgumentException("unsupported aspect annotation: " + annotationType);
    }

    public static Class<? extends Annotation> resolveAnnotationTypeFor(Class<?> adviceType) {
        Objects.requireNonNull(adviceType);
        if (MethodBeforeAdvice.class.isAssignableFrom(adviceType)) {
            return Before.class;
        }
        if (MethodAroundAdvice.class.isAssignableFrom(adviceType)) {
            return Around.class;
        }
        if (AfterReturningAdvice.class.isAssignableFrom(adviceType)) {
            return AfterReturning.class;
        }
        if (ThrowingAdvice.class.isAssignableFrom(adviceType)) {
            return AfterThrowing.class;
        }
        if (MethodAfterAdvice.class.isAssignableFrom(adviceType)) {
            return After.class;
        }
        return null;
    }
}
