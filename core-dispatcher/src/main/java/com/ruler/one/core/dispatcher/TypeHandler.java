package com.ruler.one.core.dispatcher;

public interface TypeHandler<T> {
    /**
     * 声明：本 handler 负责处理的“精确类型”
     * 例：FormsTransferParam.class
     */
    Class<T> supportsType();

    void handle(T data);
}
