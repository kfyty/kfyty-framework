package com.kfyty.boot.context.factory;

import com.kfyty.support.autoconfig.annotation.Autowired;
import com.kfyty.support.autoconfig.beans.AutowiredCapableSupport;
import com.kfyty.support.autoconfig.beans.BeanDefinition;
import com.kfyty.support.autoconfig.beans.ConditionalBeanDefinition;
import com.kfyty.support.autoconfig.beans.FactoryBeanDefinition;
import com.kfyty.support.autoconfig.beans.MethodBeanDefinition;
import com.kfyty.support.autoconfig.condition.annotation.Conditional;
import com.kfyty.support.exception.BeansException;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.kfyty.support.utils.AnnotationUtil.hasAnnotationElement;
import static java.util.Collections.synchronizedMap;

/**
 * 描述: 支持依赖注入的 bean 工厂
 *
 * @author kfyty725
 * @date 2021/7/3 10:59
 * @email kfyty725@hotmail.com
 */
@Slf4j
public abstract class AbstractAutowiredBeanFactory extends AbstractBeanFactory {
    /**
     * 条件 BeanDefinition
     */
    protected final Map<String, ConditionalBeanDefinition> conditionBeanMap = synchronizedMap(new LinkedHashMap<>());

    /**
     * 自动注入能力支持
     */
    @Autowired(AutowiredCapableSupport.BEAN_NAME)
    protected AutowiredCapableSupport autowiredCapableSupport;

    @Override
    public void resolveConditionBeanDefinitionRegistry(String name, BeanDefinition beanDefinition) {
        if (beanDefinition instanceof MethodBeanDefinition) {
            ConditionalBeanDefinition parentConditionalBeanDefinition = this.conditionBeanMap.get(((MethodBeanDefinition) beanDefinition).getParentDefinition().getBeanName());
            if (parentConditionalBeanDefinition != null || hasAnnotationElement(((MethodBeanDefinition) beanDefinition).getBeanMethod(), Conditional.class)) {
                this.registerConditionalBeanDefinition(name, new ConditionalBeanDefinition(beanDefinition, parentConditionalBeanDefinition));
                return;
            }
        }
        if (beanDefinition instanceof FactoryBeanDefinition) {
            ConditionalBeanDefinition parentConditionalBeanDefinition = this.conditionBeanMap.get(((FactoryBeanDefinition) beanDefinition).getFactoryBeanDefinition().getBeanName());
            if (parentConditionalBeanDefinition != null) {
                this.registerConditionalBeanDefinition(name, new ConditionalBeanDefinition(beanDefinition, parentConditionalBeanDefinition));
                return;
            }
        }
        if (hasAnnotationElement(beanDefinition.getBeanType(), Conditional.class)) {
            this.registerConditionalBeanDefinition(name, new ConditionalBeanDefinition(beanDefinition));
            return;
        }
        if (!conditionBeanMap.containsKey(name)) {
            super.registerBeanDefinition(name, beanDefinition);
        }
    }

    @Override
    public Object doCreateBean(BeanDefinition beanDefinition) {
        return beanDefinition.createInstance(this.applicationContext);
    }

    @Override
    public void doAutowiredBean(String beanName, Object bean) {
        if (this == bean) {
            return;
        }
        if (this.autowiredCapableSupport == null) {
            this.getBean(AutowiredCapableSupport.class);
        }
        this.autowiredCapableSupport.doAutowiredBean(bean);
    }

    public void doAutowiredLazy() {
        if (this.autowiredCapableSupport == null) {
            throw new BeansException("no bean instance found of type: " + AutowiredCapableSupport.class);
        }
        this.autowiredCapableSupport.doAutowiredLazy();
    }

    @Override
    public void close() {
        super.close();
        this.conditionBeanMap.clear();
        this.autowiredCapableSupport = null;
    }

    protected Map<String, ConditionalBeanDefinition> getConditionalBeanDefinition() {
        return this.conditionBeanMap;
    }

    protected void registerConditionalBeanDefinition(String name, ConditionalBeanDefinition conditionalBeanDefinition) {
        if (this.conditionBeanMap.containsKey(name)) {
            throw new BeansException("conflicting conditional bean definition: " + conditionalBeanDefinition.getBeanName());
        }
        this.conditionBeanMap.putIfAbsent(name, conditionalBeanDefinition);
    }
}
