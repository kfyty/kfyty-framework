package com.kfyty.boot.resolver;

import com.kfyty.boot.K;
import com.kfyty.boot.configuration.DefaultApplicationContext;
import com.kfyty.mvc.annotation.Controller;
import com.kfyty.mvc.annotation.RestController;
import com.kfyty.support.autoconfig.ApplicationContext;
import com.kfyty.support.autoconfig.BeanPostProcessor;
import com.kfyty.support.autoconfig.BeanRefreshComplete;
import com.kfyty.support.autoconfig.DestroyBean;
import com.kfyty.support.autoconfig.ImportBeanDefine;
import com.kfyty.support.autoconfig.InitializingBean;
import com.kfyty.support.autoconfig.annotation.BootApplication;
import com.kfyty.support.autoconfig.annotation.Component;
import com.kfyty.support.autoconfig.annotation.Configuration;
import com.kfyty.support.autoconfig.annotation.Order;
import com.kfyty.support.autoconfig.annotation.Repository;
import com.kfyty.support.autoconfig.annotation.Service;
import com.kfyty.support.autoconfig.beans.AutowiredProcessor;
import com.kfyty.support.autoconfig.beans.BeanDefinition;
import com.kfyty.support.autoconfig.beans.FactoryBean;
import com.kfyty.support.autoconfig.beans.GenericBeanDefinition;
import com.kfyty.support.autoconfig.beans.MethodBeanDefinition;
import com.kfyty.support.utils.BeanUtil;
import com.kfyty.support.utils.CommonUtil;
import com.kfyty.support.utils.ReflectUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 功能描述: 注解配置解析器
 *
 * @author kfyty725@hotmail.com
 * @date 2019/8/23 16:56
 * @since JDK 1.8
 */
@Slf4j
@Getter
public class AnnotationConfigResolver {
    private Class<?> primarySource;
    private DefaultApplicationContext applicationContext;
    private AutowiredProcessor autowiredProcessor;
    private FieldAnnotationResolver fieldAnnotationResolver;
    private MethodAnnotationResolver methodAnnotationResolver;

    public static AnnotationConfigResolver create(Class<?> primarySource) {
        AnnotationConfigResolver configResolver = new AnnotationConfigResolver();
        configResolver.primarySource = primarySource;
        configResolver.applicationContext = new DefaultApplicationContext(configResolver);
        configResolver.autowiredProcessor = new AutowiredProcessor(configResolver.applicationContext);
        configResolver.fieldAnnotationResolver = new FieldAnnotationResolver(configResolver);
        configResolver.methodAnnotationResolver = new MethodAnnotationResolver(configResolver);
        configResolver.registerDefaultBean();
        return configResolver;
    }

    public void registerBeanDefinition(BeanDefinition beanDefinition) {
        this.applicationContext.registerBeanDefinition(beanDefinition);
        if(FactoryBean.class.isAssignableFrom(beanDefinition.getBeanType())) {
            BeanDefinition factoryBeanDefinition = GenericBeanDefinition.from(beanDefinition);
            this.applicationContext.registerBeanDefinition(factoryBeanDefinition);
            this.methodAnnotationResolver.prepareBeanDefines(factoryBeanDefinition);
        }
        this.methodAnnotationResolver.prepareBeanDefines(beanDefinition);
    }

    public ApplicationContext doResolver(Set<Class<?>> scanClasses, String ... args) {
        try {
            this.prepareBeanDefines(scanClasses);
            this.processImportBeanDefinition(scanClasses);
            this.excludeBeanDefinition();
            this.sortBeanDefinition();

            this.instantiateBeanDefinition();

            this.fieldAnnotationResolver.doResolver();
            this.methodAnnotationResolver.doResolver();

            this.processInstantiateBean();

            this.processRefreshComplete(this.primarySource, args);

            Runtime.getRuntime().addShutdownHook(new Thread(this::processDestroy));

            return applicationContext;
        } catch (Throwable throwable) {
            log.error("k-boot started failed: {}", throwable.getMessage());
            this.processDestroy();
            throw throwable;
        }
    }

    public void doBeanPostProcessBeforeInitialization(String beanName, Object bean) {
        for (BeanPostProcessor beanPostProcessor : applicationContext.getBeanOfType(BeanPostProcessor.class).values()) {
            Object newBean = beanPostProcessor.postProcessBeforeInitialization(bean, beanName);
            if(newBean != null && newBean != bean) {
                applicationContext.replaceBean(beanName, newBean);
            }
        }
    }

    private void registerDefaultBean() {
        this.applicationContext.registerBean(ApplicationContext.class, this.applicationContext);
    }

    private void prepareBeanDefines(Set<Class<?>> scanClasses) {
        scanClasses.stream()
                .filter(e -> !ReflectUtil.isAbstract(e))
                .filter(e ->
                        e.isAnnotationPresent(BootApplication.class) ||
                                e.isAnnotationPresent(Configuration.class) ||
                                e.isAnnotationPresent(Component.class) ||
                                e.isAnnotationPresent(Controller.class) ||
                                e.isAnnotationPresent(RestController.class) ||
                                e.isAnnotationPresent(Service.class) ||
                                e.isAnnotationPresent(Repository.class))
                .map(e -> {
                    for (Annotation annotation : e.getAnnotations()) {
                        if (annotation.annotationType().isAnnotationPresent(Component.class)) {
                            String beanName = (String) ReflectUtil.invokeSimpleMethod(annotation, "value");
                            if (CommonUtil.notEmpty(beanName)) {
                                return GenericBeanDefinition.from(beanName, e);
                            }
                        }
                    }
                    return GenericBeanDefinition.from(e);
                })
                .forEach(this::registerBeanDefinition);
    }

    private void processImportBeanDefinition(Set<Class<?>> scanClasses) {
        Set<BeanDefinition> importBeanDefines = this.applicationContext.getBeanDefinitions().values().stream().filter(e -> ImportBeanDefine.class.isAssignableFrom(e.getBeanType())).collect(Collectors.toSet());
        for (BeanDefinition importBeanDefine : importBeanDefines) {
            ImportBeanDefine bean = (ImportBeanDefine) this.applicationContext.registerBean(importBeanDefine);
            bean.doImport(scanClasses).forEach(this::registerBeanDefinition);
        }
    }

    private void excludeBeanDefinition() {
        Iterator<BeanDefinition> iterator = this.applicationContext.getBeanDefinitions().values().iterator();
        while (iterator.hasNext()) {
            BeanDefinition beanDefinition = iterator.next();
            if(K.isExclude(beanDefinition.getBeanName()) || K.isExclude(beanDefinition.getBeanType())) {
                iterator.remove();
                log.info("exclude bean definition: {}", beanDefinition);
            }
        }
    }

    private void sortBeanDefinition() {
        Map<String, BeanDefinition> beanDefinitions = this.applicationContext.getBeanDefinitions();
        Map<String, BeanDefinition> sortBeanDefinition = beanDefinitions.values()
                .stream()
                .sorted((define1, define2) -> {
                    if(BeanPostProcessor.class.isAssignableFrom(define1.getBeanType()) && !BeanPostProcessor.class.isAssignableFrom(define2.getBeanType())) {
                        return Order.HIGHEST_PRECEDENCE;
                    }
                    return BeanUtil.getBeanOrder(define1) - BeanUtil.getBeanOrder(define2);
                })
                .collect(Collectors.toMap(BeanDefinition::getBeanName, Function.identity(), (k1, k2) -> {
                    throw new IllegalStateException("duplicate key " + k2);
                }, LinkedHashMap::new));
        synchronized (this.applicationContext.getBeanDefinitions()) {
            beanDefinitions.clear();
            beanDefinitions.putAll(sortBeanDefinition);
        }
    }

    private void instantiateBeanDefinition() {
        for (BeanDefinition beanDefinition : this.applicationContext.getBeanDefinitions().values()) {
            this.applicationContext.registerBean(beanDefinition);
        }
    }

    private void processInstantiateBean() {
        applicationContext.getBeanOfType(InitializingBean.class).values().forEach(InitializingBean::afterPropertiesSet);

        this.processBeanMethod(e -> e.getInitMethod(this.applicationContext) != null, MethodBeanDefinition::getInitMethod);

        for (BeanPostProcessor beanPostProcessor : applicationContext.getBeanOfType(BeanPostProcessor.class).values()) {
            this.applicationContext.doInBeans((beanName, bean) -> {
                Object newBean = beanPostProcessor.postProcessAfterInitialization(bean, beanName);
                if(newBean != null && newBean != bean) {
                    applicationContext.replaceBean(beanName, newBean);
                }
            });
        }
    }

    private void processRefreshComplete(Class<?> clazz, String ... args) {
        for (BeanRefreshComplete bean : applicationContext.getBeanOfType(BeanRefreshComplete.class).values()) {
            bean.onComplete(clazz, args);
        }
    }

    private void processDestroy() {
        log.info("destroy bean...");

        for (BeanPostProcessor beanPostProcessor : applicationContext.getBeanOfType(BeanPostProcessor.class).values()) {
            this.applicationContext.doInBeans((beanName, bean) -> beanPostProcessor.postProcessBeforeDestroy(bean, beanName));
        }

        applicationContext.getBeanOfType(DestroyBean.class).values().forEach(DestroyBean::onDestroy);

        this.processBeanMethod(e -> e.getDestroyMethod(this.applicationContext) != null, MethodBeanDefinition::getDestroyMethod);
    }

    private void processBeanMethod(Predicate<MethodBeanDefinition> methodPredicate, Function<MethodBeanDefinition, Method> methodMapping) {
        Iterator<BeanDefinition> iterator = this.applicationContext.getBeanDefinitions().values().stream().filter(e -> e instanceof MethodBeanDefinition).iterator();
        while (iterator.hasNext()) {
            MethodBeanDefinition beanDefinition = (MethodBeanDefinition) iterator.next();
            if(methodPredicate.test(beanDefinition)) {
                ReflectUtil.invokeMethod(applicationContext.getBean(beanDefinition.getBeanName()), methodMapping.apply(beanDefinition));
            }
        }
    }
}
