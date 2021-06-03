package com.kfyty.support.jdbc;

import com.kfyty.support.exception.SupportException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.Map;

/**
 * 功能描述: 返回值类型
 *
 * @author kfyty725@hotmail.com
 * @date 2019/8/20 10:16
 * @since JDK 1.8
 */
@Data
@Slf4j
@NoArgsConstructor
public class ReturnType<T, K, V> {
    private String key;
    private Boolean array;
    private Boolean parameterizedType;
    private Class<T> returnType;
    private Class<K> firstParameterizedType;
    private Class<V> secondParameterizedType;

    public ReturnType(Boolean array, Boolean parameterizedType, Class<T> returnType, Type firstParameterizedType, Type secondParameterizedType) {
        this.array = array;
        this.parameterizedType = parameterizedType;
        this.returnType = returnType;
        this.setFirstParameterizedType(firstParameterizedType);
        this.setSecondParameterizedType(secondParameterizedType);
    }

    public boolean isArray() {
        return this.array;
    }

    public boolean isParameterizedType() {
        return this.parameterizedType;
    }

    @SuppressWarnings("unchecked")
    public void setFirstParameterizedType(Type firstParameterizedType) {
        if(firstParameterizedType == null) {
            return ;
        }
        if(firstParameterizedType instanceof Class) {
            this.firstParameterizedType = (Class<K>) firstParameterizedType;
            return ;
        }
        this.firstParameterizedType = (Class<K>) ((WildcardType) firstParameterizedType).getUpperBounds()[0];
    }

    @SuppressWarnings("unchecked")
    public void setSecondParameterizedType(Type secondParameterizedType) {
        if(secondParameterizedType == null) {
            return ;
        }
        if(secondParameterizedType instanceof Class) {
            this.secondParameterizedType = (Class<V>) secondParameterizedType;
            return ;
        }
        this.secondParameterizedType = (Class<V>) ((WildcardType) secondParameterizedType).getUpperBounds()[0];
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T, K, V> ReturnType<T, K, V> getReturnType(Type genericType, Class<T> type) {
        if(type.isArray()) {
            return new ReturnType(true, false, type.getComponentType(), null, null);
        }
        if(!(genericType instanceof ParameterizedType)) {
            return new ReturnType<>(false, false, type, null, null);
        }
        ParameterizedType parameterizedType = (ParameterizedType) genericType;
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        if(Collection.class.isAssignableFrom(type) || Class.class.isAssignableFrom(type)) {
            if(actualTypeArguments[0] instanceof ParameterizedType) {
                Type rawType = ((ParameterizedType) actualTypeArguments[0]).getRawType();
                Type[] types = ((ParameterizedType) actualTypeArguments[0]).getActualTypeArguments();
                if(!Map.class.isAssignableFrom((Class<?>) rawType)) {
                    throw new SupportException("nested parameterized type must be map !");
                }
                return new ReturnType<>(false, true, type, types[0], types[1]);
            }
            return new ReturnType<>(false, true, type, actualTypeArguments[0], null);
        }
        if(Map.class.isAssignableFrom(type)) {
            if(actualTypeArguments.length != 2) {
                throw new SupportException("parameterized type only support collection and map !");
            }
            return new ReturnType<>(false, true, type, actualTypeArguments[0], actualTypeArguments[1]);
        }
        throw new SupportException("parse return type failed !");
    }
}
