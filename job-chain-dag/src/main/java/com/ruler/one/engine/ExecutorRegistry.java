package com.ruler.one.engine;

import com.ruler.one.plugins.NodeExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

//@Component
public class ExecutorRegistry {
    private final Map<String, NodeExecutor> byType = new HashMap<>();

    public ExecutorRegistry(List<NodeExecutor> executors) {
        for (NodeExecutor ex : executors) {
            byType.put(ex.type(), ex);
        }
    }

    public NodeExecutor get(String type) {
        NodeExecutor ex = byType.get(type);
        if (ex == null) throw new IllegalArgumentException("No executor for type: " + type);
        return ex;
    }
}
