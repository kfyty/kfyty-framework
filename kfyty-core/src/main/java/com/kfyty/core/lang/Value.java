package com.kfyty.core.lang;

import com.kfyty.core.autoconfig.annotation.NestedConfigurationProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 描述: 包装一个值
 *
 * @author kfyty725
 * @date 2021/9/19 11:08
 * @email kfyty725@hotmail.com
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@NestedConfigurationProperty
public class Value<T> {
    private T value;

    public T get() {
        return this.getValue();
    }

    public void set(T value) {
        this.setValue(value);
    }

    @Override
    public int hashCode() {
        return this.value == null ? 0 : this.value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Value && Objects.equals(this.value, ((Value<?>) obj).value);
    }

    @Override
    public String toString() {
        return String.valueOf(this.value);
    }
}
