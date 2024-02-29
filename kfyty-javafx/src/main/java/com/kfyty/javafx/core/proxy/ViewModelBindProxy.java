package com.kfyty.javafx.core.proxy;

import com.kfyty.core.proxy.MethodInterceptorChain;
import com.kfyty.core.proxy.MethodInterceptorChainPoint;
import com.kfyty.core.proxy.MethodProxy;
import com.kfyty.core.support.Pair;
import com.kfyty.core.utils.AopUtil;
import com.kfyty.core.utils.PackageUtil;
import com.kfyty.core.utils.ReflectUtil;
import com.kfyty.javafx.core.BootstrapApplication;
import com.kfyty.javafx.core.LifeCycleController;
import com.kfyty.javafx.core.ViewBindCapableController;
import com.kfyty.javafx.core.binder.ViewPropertyBinder;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 描述: 模型绑定代理
 *
 * @author kfyty725
 * @date 2024/2/21 11:56
 * @email kfyty725@hotmail.com
 */
@RequiredArgsConstructor
public class ViewModelBindProxy implements MethodInterceptorChainPoint {
    /**
     * 控制器
     */
    private final ViewBindCapableController controller;

    /**
     * 视图模型绑定列表
     */
    private final List<Pair<String, ObservableValue<?>>> bindViews;

    /**
     * 属性绑定器
     */
    private static volatile List<ViewPropertyBinder> viewPropertyBinders;

    public void addBindView(String bindPath, ObservableValue<?> view) {
        this.bindViews.add(new Pair<>(bindPath, view));
    }

    @Override
    public Object proceed(MethodProxy methodProxy, MethodInterceptorChain chain) throws Throwable {
        int hashCode = methodProxy.getTarget().hashCode();
        Object proceed = chain.proceed(methodProxy);
        int proceedHashCode = methodProxy.getTarget().hashCode();
        if (hashCode != proceedHashCode) {
            this.viewBind(methodProxy);
        }
        return proceed;
    }

    public void viewBind(MethodProxy methodProxy) {
        // 事件过来的不处理
        for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
            if (stackTraceElement.getClassName().equals(ViewBindCapableController.ViewBindEventHandler.class.getName())) {
                return;
            }
        }

        // 更新到视图
        for (Pair<String, ObservableValue<?>> bindView : this.bindViews) {
            Object viewValue = bindView.getValue().getValue();
            Object modelValue = ReflectUtil.parseValue(bindView.getKey(), this.controller);
            if (!Objects.equals(viewValue, modelValue)) {
                try {
                    this.viewBind(bindView.getValue(), modelValue);
                } catch (Throwable e) {
                    if (this.controller instanceof LifeCycleController lifeCycleController) {
                        lifeCycleController.onViewBindCause(bindView.getValue(), modelValue, e);
                        return;
                    }
                    throw e;
                }
            }
        }
    }

    public void viewBind(ObservableValue<?> view, Object value) {
        this.obtainViewPropertyBinder();
        for (ViewPropertyBinder binder : viewPropertyBinders) {
            if (view instanceof WritableValue<?> writableValue) {
                if (binder.support(writableValue, view.getClass())) {
                    binder.bind(writableValue, value);
                }
            }
        }
    }

    protected void obtainViewPropertyBinder() {
        if (viewPropertyBinders == null) {
            synchronized (ViewModelBindProxy.class) {
                if (viewPropertyBinders == null) {
                    viewPropertyBinders = PackageUtil.scanInstance(ViewPropertyBinder.class);
                    viewPropertyBinders.addAll(BootstrapApplication.getApplicationContext().getBeanOfType(ViewPropertyBinder.class).values());
                }
            }
        }
    }

    public static void triggerViewBind(Object controller) {
        Map<String, Field> fieldMap = ReflectUtil.getFieldMap(controller.getClass());
        for (Field value : fieldMap.values()) {
            Object fieldValue = ReflectUtil.getFieldValue(controller, value, false);
            if (fieldValue != null && AopUtil.isProxy(fieldValue)) {
                AopUtil.getProxyInterceptorChain(fieldValue)
                        .getChainPoints()
                        .stream()
                        .filter(e -> e instanceof ViewModelBindProxy)
                        .map(e -> (ViewModelBindProxy) e)
                        .findAny().ifPresent(proxy -> proxy.viewBind(null));
            }
        }
    }
}
