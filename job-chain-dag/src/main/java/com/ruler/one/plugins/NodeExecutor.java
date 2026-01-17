package com.ruler.one.plugins;

import com.ruler.one.runtime.NodeContext;
import com.ruler.one.runtime.NodeResult;

import java.util.Map;

public interface NodeExecutor {
    String type();
    NodeResult execute(NodeContext ctx, Map<String, Object> cfg) throws Exception;
}
