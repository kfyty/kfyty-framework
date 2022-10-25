package com.kfyty.boot.context;

import com.kfyty.boot.context.factory.AbstractAutowiredBeanFactory;
import com.kfyty.support.autoconfig.ApplicationContext;
import com.kfyty.support.autoconfig.BeanFactoryPostProcessor;
import com.kfyty.support.autoconfig.BeanPostProcessor;
import com.kfyty.support.autoconfig.ContextAfterRefreshed;
import com.kfyty.support.autoconfig.annotation.Autowired;
import com.kfyty.support.autoconfig.annotation.ComponentFilter;
import com.kfyty.support.autoconfig.beans.BeanDefinition;
import com.kfyty.support.autoconfig.beans.BeanFactory;
import com.kfyty.support.autoconfig.beans.ConditionalBeanDefinition;
import com.kfyty.support.autoconfig.condition.ConditionContext;
import com.kfyty.support.event.ApplicationEvent;
import com.kfyty.support.event.ApplicationEventPublisher;
import com.kfyty.support.event.ApplicationListener;
import com.kfyty.support.event.ContextRefreshedEvent;
import com.kfyty.support.utils.CommonUtil;
import com.kfyty.support.wrapper.AnnotationWrapper;
import com.kfyty.support.wrapper.Pair;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.kfyty.support.autoconfig.beans.BeanDefinition.BEAN_DEFINITION_COMPARATOR;
import static com.kfyty.support.autoconfig.beans.builder.BeanDefinitionBuilder.genericBeanDefinition;
import static com.kfyty.support.utils.AnnotationUtil.hasAnnotationElement;
import static com.kfyty.support.utils.ReflectUtil.isAbstract;

/**
 * 描述: 上下文基础实现
 *
 * @author kfyty725
 * @date 2021/7/3 11:05
 * @email kfyty725@hotmail.com
 */
@Slf4j
public abstract class AbstractApplicationContext extends AbstractAutowiredBeanFactory implements ApplicationContext {
    private final Thread shutdownHook = new Thread(this::close);

    protected String[] commanderArgs;
    protected Class<?> primarySource;
    protected Set<Class<?>> scanClasses;
    protected List<AnnotationWrapper<ComponentFilter>> includeFilterAnnotations = new ArrayList<>(4);
    protected List<AnnotationWrapper<ComponentFilter>> excludeFilterAnnotations = new ArrayList<>(4);

    @Autowired
    protected ApplicationEventPublisher applicationEventPublisher;

    public Set<Class<?>> getScanClasses() {
        return this.scanClasses;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = this;
    }

    @Override
    public Class<?> getPrimarySource() {
        return this.primarySource;
    }

    @Override
    public String[] getCommandLineArgs() {
        return this.commanderArgs;
    }

    @Override
    public ApplicationContext refresh() {
        synchronized (this) {
            try {
                /* 刷新前的准备，由子类扩展 */
                this.beforeRefresh();

                /* 解析 bean 定义 */
                this.prepareBeanDefines(this.scanClasses);

                /* 执行 bean 工厂后置处理器 */
                this.invokeBeanFactoryPostProcessor();

                /* 注册 bean 后置处理器 */
                this.registerBeanPostProcessors();

                /* 实例化单例 bean 定义 */
                this.instantiateBeanDefinition();

                /* 子类扩展 */
                this.onRefresh();

                /* 子类扩展 */
                this.afterRefresh();

                /* 添加销毁钩子 */
                Runtime.getRuntime().addShutdownHook(shutdownHook);

                /* 发布刷新完成事件 */
                this.publishEvent(new ContextRefreshedEvent(this));

                return this;
            } catch (Throwable throwable) {
                log.error("k-boot started failed !");
                try {
                    this.close();
                } catch (Throwable nestedThrowable) {
                    log.error("close application context error !", nestedThrowable);
                }
                throw throwable;
            }
        }
    }

    /**
     * 根据组件过滤器进行匹配
     * 排除过滤：
     * 若返回 true，则排除过滤匹配失败，继续执行包含过滤
     * 若返回 false，说明可能被排除，此时需继续判断该注解的声明是否被排除
     * 包含过滤：
     * 直接返回即可
     *
     * @param beanClass 目标 bean class
     * @return 该 bean class 是否能够生成 bean 定义
     */
    @Override
    public boolean doFilterComponent(Class<?> beanClass) {
        Pair<Boolean, AnnotationWrapper<ComponentFilter>> exclude = this.doFilterComponent(this.excludeFilterAnnotations, beanClass, false);
        if (!exclude.getKey() && exclude.getValue() != null) {
            return !doFilterComponent(exclude.getValue().getDeclaring());
        }
        Pair<Boolean, AnnotationWrapper<ComponentFilter>> include = this.doFilterComponent(this.includeFilterAnnotations, beanClass, true);
        return include.getKey();
    }

    @Override
    public void publishEvent(ApplicationEvent<?> event) {
        this.applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void registerEventListener(ApplicationListener<?> applicationListener) {
        this.applicationEventPublisher.registerEventListener(applicationListener);
    }

    @Override
    public void close() {
        super.close();
        this.applicationEventPublisher = null;
    }

    /**
     * 根据组件过滤器进行匹配
     *
     * @param componentFilterWrappers 组件过滤条件
     * @param beanClass               目标 bean class
     * @param isInclude               当前匹配排除还是包含
     * @return 匹配结果，以及对应的过滤组件
     */
    protected Pair<Boolean, AnnotationWrapper<ComponentFilter>> doFilterComponent(List<AnnotationWrapper<ComponentFilter>> componentFilterWrappers, Class<?> beanClass, boolean isInclude) {
        for (AnnotationWrapper<ComponentFilter> componentFilterWrapper : componentFilterWrappers) {
            ComponentFilter componentFilter = componentFilterWrapper.get();
            if (Arrays.stream(componentFilter.value()).anyMatch(beanClass.getName()::startsWith)) {
                return new Pair<>(isInclude, componentFilterWrapper);
            }
            if (Arrays.asList(componentFilter.classes()).contains(beanClass)) {
                return new Pair<>(isInclude, componentFilterWrapper);
            }
            if (Arrays.stream(componentFilter.annotations()).anyMatch(e -> hasAnnotationElement(beanClass, e))) {
                return new Pair<>(isInclude, componentFilterWrapper);
            }
        }
        return new Pair<>(!isInclude, null);
    }

    protected void beforeRefresh() {
        this.close();
        this.registerDefaultBean();
        Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
    }

    protected void onRefresh() {

    }

    protected void afterRefresh() {
        super.autowiredLazy();
        this.getBeanOfType(ContextAfterRefreshed.class).values().forEach(e -> e.onAfterRefreshed(this));
    }

    protected void registerDefaultBean() {
        this.registerBean(BeanFactory.class, this);
        this.registerBean(ApplicationContext.class, this);
    }

    protected void prepareBeanDefines(Set<Class<?>> scanClasses) {
        scanClasses.stream().filter(e -> !isAbstract(e) && this.doFilterComponent(e)).map(e -> genericBeanDefinition(e).getBeanDefinition()).forEach(this::registerBeanDefinition);
        this.resolveConditionBeanDefines();
    }

    protected void resolveConditionBeanDefines() {
        ConditionContext conditionContext = this.getConditionContext();
        Map<String, ConditionalBeanDefinition> conditionalBeanDefinition = CommonUtil.sort(this.getConditionalBeanDefinition(), (b1, b2) -> BEAN_DEFINITION_COMPARATOR.compare(b1.getValue().getBeanDefinition(), b2.getValue().getBeanDefinition()));
        for (ConditionalBeanDefinition value : conditionalBeanDefinition.values()) {
            if (!conditionContext.shouldSkip(value) && !value.isRegistered()) {
                value.setRegistered(true);
                this.registerBeanDefinition(value.getBeanName(), value.getBeanDefinition());
            }
        }
    }

    protected void invokeBeanFactoryPostProcessor() {
        Map<String, BeanDefinition> beanFactoryPostProcessors = this.getBeanDefinitions(e -> BeanFactoryPostProcessor.class.isAssignableFrom(e.getValue().getBeanType()));
        for (BeanDefinition beanDefinition : beanFactoryPostProcessors.values()) {
            BeanFactoryPostProcessor beanFactoryPostProcessor = (BeanFactoryPostProcessor) this.registerBean(beanDefinition);
            beanFactoryPostProcessor.postProcessBeanFactory(this);
        }
        this.resolveConditionBeanDefines();
    }

    protected void registerBeanPostProcessors() {
        Map<String, BeanDefinition> beanPostProcessors = this.getBeanDefinitions(e -> BeanPostProcessor.class.isAssignableFrom(e.getValue().getBeanType()));
        for (BeanDefinition beanDefinition : beanPostProcessors.values()) {
            this.registerBeanPostProcessors((BeanPostProcessor) this.registerBean(beanDefinition));
        }
    }

    protected void instantiateBeanDefinition() {
        this.sortBeanDefinition();
        for (BeanDefinition beanDefinition : this.getBeanDefinitions().values()) {
            if (beanDefinition.isSingleton()) {
                this.registerBean(beanDefinition);
            }
        }
    }

    protected void sortBeanDefinition() {
        synchronized (this.getBeanDefinitions()) {
            Map<String, BeanDefinition> sortBeanDefinition = this.getBeanDefinitions(e -> true);
            beanDefinitions.clear();
            beanDefinitions.putAll(sortBeanDefinition);
        }
    }
}
