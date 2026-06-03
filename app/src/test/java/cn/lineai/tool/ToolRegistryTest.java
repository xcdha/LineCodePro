package cn.lineai.tool;

import org.junit.Assert;
import org.junit.Test;

public final class ToolRegistryTest {
    @Test
    public void registersWebTools() {
        ToolRegistry registry = new ToolRegistry();

        Assert.assertNotNull(registry.get("web_search"));
        Assert.assertNotNull(registry.get("web_fetch"));
    }

    @Test
    public void registersAgentTools() {
        ToolRegistry registry = new ToolRegistry();

        Assert.assertNotNull(registry.get("agent"));
        Assert.assertNotNull(registry.get("agent_pipeline"));
    }
}
