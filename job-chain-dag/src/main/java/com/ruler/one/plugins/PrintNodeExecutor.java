package com.ruler.one.plugins;

import com.ruler.one.runtime.NodeContext;
import com.ruler.one.runtime.NodeResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PrintNodeExecutor implements NodeExecutor {
    @Override
    public String type() {
        return "print";
    }

    @Override
    public NodeResult execute(NodeContext ctx, Map<String, Object> cfg) {
        System.out.println("[PrintNodeExecutor] nodeId=" + ctx.getNodeId() + ", cfg=" + cfg);
        NodeResult result = NodeResult.ok();
        result.setArtifact(cfg); // 产物就是参数本身
        return result;
    }
}
