package com.kfyty.configuration;

import com.kfyty.generate.GenerateSources;
import com.kfyty.generate.configuration.GenerateConfigurable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;

/**
 * 功能描述: 应用配置
 *
 * @author kfyty725@hotmail.com
 * @date 2019/8/23 16:33
 * @since JDK 1.8
 */
@Slf4j
public class ApplicationConfigurable {
    @Getter
    private GenerateConfigurable generateConfigurable;

    private ApplicationConfigurable() {

    }

    public static ApplicationConfigurable initApplicationConfigurable() throws Exception {
        ApplicationConfigurable applicationConfigurable = new ApplicationConfigurable();
        return applicationConfigurable;
    }

    public void initAutoConfiguration() throws Exception {
        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields) {
            if(!Configuration.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object o = field.getType().newInstance();
            field.set(this, o);
            o.getClass().getMethod("enableAutoConfiguration").invoke(o);
        }
        if(log.isDebugEnabled()) {
            log.debug(": initialize auto configuration success !");
        }
    }

    public void autoConfigurationAfterCheck() throws Exception {
        Field[] fields = this.getClass().getDeclaredFields();
        for (Field field : fields) {
            if(!Configuration.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object o = field.get(this);
            o.getClass().getMethod("autoConfigurationAfterCheck").invoke(o);
        }
        if(log.isDebugEnabled()) {
            log.debug(": auto configuration after check success !");
        }
    }

    public void executeAutoGenerateSources() throws Exception {
        if(!this.getGenerateConfigurable().isAutoConfiguration()) {
            return;
        }
        new GenerateSources(generateConfigurable).generate();
    }
}