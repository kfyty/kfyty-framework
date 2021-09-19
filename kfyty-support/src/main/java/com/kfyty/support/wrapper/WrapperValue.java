package com.kfyty.support.wrapper;

import lombok.AllArgsConstructor;

import java.util.Objects;

/**
 * 描述: 包装一个值
 *
 * @author kfyty725
 * @date 2021/9/19 11:08
 * @email kfyty725@hotmail.com
 */
@AllArgsConstructor
public class WrapperValue<T> {
    private T value;

    public T get() {
        return this.value;
    }

    public void set(T value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public int hashCode() {
        return this.value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof WrapperValue && Objects.equals(this.value, ((WrapperValue<?>) obj).value);
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}
