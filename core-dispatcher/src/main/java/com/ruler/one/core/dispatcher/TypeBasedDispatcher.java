package com.ruler.one.core.dispatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Type-Driven Dispatcher:
 * - 构造时把 Spring 注入的 Map(beanName -> handler) 变换为 Map(type -> handler)
 * - 启动期 fail-fast：同一 type 不允许多个 handler
 * - 运行期 O(1) 路由：按 data.getClass() 精确匹配
 */
public class TypeBasedDispatcher<T> {

    private final Map<Class<? extends T>, TypeHandler<? extends T>> byType;

    public TypeBasedDispatcher(Map<String, ? extends TypeHandler<? extends T>> handlers) {
        Objects.requireNonNull(handlers, "handlers must not be null");

        Map<Class<? extends T>, TypeHandler<? extends T>> tmp = new HashMap<>();

        for (Map.Entry<String, ? extends TypeHandler<? extends T>> e : handlers.entrySet()) {
            String beanName = e.getKey();
            TypeHandler<? extends T> handler = e.getValue();

            Class<? extends T> type = handler.supportsType();
            if (type == null) {
                throw new IllegalStateException("Handler supportsType() returned null, handler=" + handler.getClass().getName()
                        + ", beanName=" + beanName);
            }

            TypeHandler<? extends T> old = tmp.put(type, handler);
            if (old != null) {
                throw new IllegalStateException(
                        "Duplicate handler for type=" + type.getName()
                                + ", old=" + old.getClass().getName()
                                + ", new=" + handler.getClass().getName()
                                + ", beanName=" + beanName
                );
            }
        }

        this.byType = Map.copyOf(tmp);
    }

    /**
     * 精确匹配分发：data.getClass()
     * （如果你后面需要“子类匹配父类 handler”，再扩展一个 dispatchAssignable）
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void dispatch(T data) {
        if (data == null) {
            throw new IllegalArgumentException("data is null");
        }

        TypeHandler handler = byType.get(data.getClass());
        if (handler == null) {
            throw new IllegalArgumentException("No handler for type: " + data.getClass().getName()
                    + ", available=" + byType.keySet());
        }

        handler.handle(data);
    }

    public Map<Class<? extends T>, TypeHandler<? extends T>> routingTable() {
        return byType;
    }
}
