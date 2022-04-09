package com.kfyty.support.proxy.factory;

import com.kfyty.support.autoconfig.beans.BeanDefinition;
import com.kfyty.support.proxy.MethodInterceptorChain;
import com.kfyty.support.utils.CommonUtil;
import com.kfyty.support.utils.ReflectUtil;
import lombok.NoArgsConstructor;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;

import static com.kfyty.support.utils.CommonUtil.EMPTY_CLASS_ARRAY;
import static com.kfyty.support.utils.CommonUtil.EMPTY_OBJECT_ARRAY;

/**
 * 描述: cglib 动态代理工厂
 *
 * @author kfyty725
 * @date 2021/6/19 11:50
 * @email kfyty725@hotmail.com
 */
@NoArgsConstructor
public class CglibDynamicProxyFactory extends DynamicProxyFactory {
    public static final Callback[] EMPTY_CGLIB_CALLBACK_ARRAY = new Callback[0];

    @Override
    public <T> T createProxy(T source, BeanDefinition beanDefinition) {
        return createProxy(source, beanDefinition.getConstructArgTypes(), beanDefinition.getConstructArgValues());
    }

    @Override
    public <T> T createProxy(T source, Class<T> targetClass, Class<?>[] argTypes, Object[] argValues) {
        return createProxy(source, targetClass, argTypes, argValues, EMPTY_CGLIB_CALLBACK_ARRAY);
    }

    public <T> T createProxy(T source, Callback... callbacks) {
        //noinspection unchecked
        return createProxy((Class<T>) source.getClass(), callbacks);
    }

    public <T> T createProxy(Class<T> targetClass, Callback... callbacks) {
        return createProxy(null, targetClass, EMPTY_CLASS_ARRAY, EMPTY_OBJECT_ARRAY, callbacks);
    }

    @SuppressWarnings("unchecked")
    public <T> T createProxy(T source, Class<T> targetClass, Class<?>[] argTypes, Object[] argValues, Callback... callbacks) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(targetClass);
        enhancer.setInterfaces(ReflectUtil.getInterfaces(targetClass));
        enhancer.setCallback(new MethodInterceptorChain(source));
        if (CommonUtil.notEmpty(callbacks)) {
            enhancer.setCallbacks(callbacks);
        }
        return (T) enhancer.create(argTypes, argValues);
    }
}
