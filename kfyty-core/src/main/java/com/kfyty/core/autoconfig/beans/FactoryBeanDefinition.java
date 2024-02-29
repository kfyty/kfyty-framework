package com.kfyty.core.autoconfig.beans;

import com.kfyty.core.autoconfig.ApplicationContext;
import com.kfyty.core.lang.util.concurrent.WeakConcurrentHashMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.kfyty.core.utils.BeanUtil.removeFactoryBeanNamePrefix;
import static com.kfyty.core.utils.ReflectUtil.newInstance;

/**
 * 描述: FactoryBean 类型的 bean 定义所衍生的 bean 定义
 *
 * @author kfyty725
 * @date 2021/6/12 12:06
 * @email kfyty725@hotmail.com
 */
@Slf4j
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FactoryBeanDefinition extends GenericBeanDefinition {
    /**
     * FactoryBean<T> 本身的 bean name 前缀
     */
    public static final String FACTORY_BEAN_PREFIX = "&";

    /**
     * 临时 FactoryBean 对象缓存，用于获取目标 bean 定义信息
     */
    private static final Map<String, FactoryBean<?>> snapFactoryBeanCache = new WeakConcurrentHashMap<>();

    /**
     * 该 bean 定义所在的工厂 bean 定义
     * 该工厂 bean 定义的构造方法，必须有一个符合 defaultConstructorArgs 参数的构造器，以支持构造临时对象
     * 其实际实例化时的构造器，可用 Autowired 进行标注
     */
    @Getter
    private final BeanDefinition factoryBeanDefinition;

    public FactoryBeanDefinition(BeanDefinition factoryBeanDefinition) {
        this(getSnapFactoryBean(factoryBeanDefinition), factoryBeanDefinition);
    }

    private FactoryBeanDefinition(FactoryBean<?> temp, BeanDefinition factoryBeanDefinition) {
        super(removeFactoryBeanNamePrefix(factoryBeanDefinition.getBeanName()), temp.getBeanType(), factoryBeanDefinition.getScope(), factoryBeanDefinition.isScopeProxy(), factoryBeanDefinition.isLazyInit(), factoryBeanDefinition.isLazyProxy());
        this.factoryBeanDefinition = factoryBeanDefinition;
        if (temp.isSingleton()) {
            this.factoryBeanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);
        }
    }

    /**
     * 因为该 bean 可能被注入到自身而导致递归提前创建，因此执行方法后需要再次判断
     */
    @Override
    public Object createInstance(ApplicationContext context) {
        if (context.contains(this.getBeanName())) {
            return context.getBean(this.getBeanName());
        }
        FactoryBean<?> factoryBean = (FactoryBean<?>) context.registerBean(this.factoryBeanDefinition);
        if (context.contains(this.getBeanName())) {
            return context.getBean(this.getBeanName());
        }
        Object bean = factoryBean.getObject();
        if (log.isDebugEnabled()) {
            log.debug("instantiate bean from factory bean: {} !", bean);
        }
        return bean;
    }

    public static Class<?> getSnapBeanType(String beanName, Class<?> defaultBeanType) {
        FactoryBean<?> factoryBean = snapFactoryBeanCache.get(beanName);
        return factoryBean == null ? defaultBeanType : factoryBean.getBeanType();
    }

    public static FactoryBean<?> getSnapFactoryBean(BeanDefinition beanDefinition) {
        return snapFactoryBeanCache.computeIfAbsent(beanDefinition.getBeanName(), k -> (FactoryBean<?>) newInstance(beanDefinition.getBeanType(), ((GenericBeanDefinition) beanDefinition).defaultConstructorArgs));
    }

    public static void addSnapFactoryBeanCache(String beanName, FactoryBean<?> factoryBean) {
        snapFactoryBeanCache.putIfAbsent(beanName, factoryBean);
    }
}
