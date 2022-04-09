package com.kfyty.boot.processor;

import com.kfyty.boot.event.EventListenerAnnotationListener;
import com.kfyty.support.autoconfig.ApplicationContext;
import com.kfyty.support.autoconfig.BeanPostProcessor;
import com.kfyty.support.autoconfig.annotation.Autowired;
import com.kfyty.support.autoconfig.annotation.Component;
import com.kfyty.support.autoconfig.annotation.EventListener;
import com.kfyty.support.autoconfig.annotation.Order;
import com.kfyty.support.event.ApplicationEvent;
import com.kfyty.support.event.ApplicationEventPublisher;
import com.kfyty.support.utils.AnnotationUtil;
import com.kfyty.support.utils.AopUtil;
import com.kfyty.support.utils.CommonUtil;
import com.kfyty.support.utils.ReflectUtil;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * 描述: EventListener 注解处理器
 *
 * @author kfyty725
 * @date 2021/6/21 18:02
 * @email kfyty725@hotmail.com
 */
@Slf4j
@Component
@Order(Order.HIGHEST_PRECEDENCE + 200)
public class EventListenerAnnotationBeanPostProcessor implements BeanPostProcessor {
    @Autowired
    private ApplicationContext context;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        Class<?> beanClass = AopUtil.getTargetClass(bean);
        for (Method method : ReflectUtil.getMethods(beanClass)) {
            if(AnnotationUtil.hasAnnotation(method, EventListener.class)) {
                Method listenerMethod = AopUtil.getInterfaceMethod(bean, method);
                this.createEventListener(beanName, listenerMethod, AnnotationUtil.findAnnotation(method, EventListener.class));
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void createEventListener(String beanName, Method listenerMethod, EventListener eventListener) {
        Class<? extends ApplicationEvent<?>>[] eventTypes = eventListener.value();
        if(CommonUtil.empty(eventTypes)) {
            eventTypes = (Class<? extends ApplicationEvent<?>>[]) listenerMethod.getParameterTypes();
        }
        for (Class<? extends ApplicationEvent<?>> eventType : eventTypes) {
            EventListenerAnnotationListener annotationListener = new EventListenerAnnotationListener(beanName, listenerMethod, eventType, this.context);
            this.applicationEventPublisher.registerEventListener(annotationListener);
            log.info("register annotation event listener: {}", annotationListener);
        }
    }
}
