package lol.pbu.mcp;

import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.mcp.annotations.McpPrimitive;
import io.micronaut.mcp.annotations.Tool;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers and indexes all {@link Tool} annotated executable methods
 * to enable non-blocking reactive MCP tool execution.
 */
@Singleton
public class ReactiveToolMethodRegistry implements ExecutableMethodProcessor<McpPrimitive> {

    private static final Logger log = LoggerFactory.getLogger(ReactiveToolMethodRegistry.class);
    private static final String DEFAULT_NAME = "<<element name>>";

    private final Map<String, ToolMethodEntry> toolsByName = new ConcurrentHashMap<>();

    @Override
    public <B> void process(@NonNull BeanDefinition<B> beanDefinition, @NonNull ExecutableMethod<B, ?> method) {
        if (!method.hasStereotype(Tool.class)) {
            return;
        }
        String toolName = method.stringValue(Tool.class, "name").orElse(DEFAULT_NAME);
        if (DEFAULT_NAME.equals(toolName)) {
            toolName = method.getName();
        }
        log.debug("Registering reactive tool method: {} -> {}.{}", toolName, beanDefinition.getBeanType().getSimpleName(), method.getName());
        toolsByName.put(toolName, new ToolMethodEntry(beanDefinition, method));
    }

    @Nullable
    public ToolMethodEntry find(@NonNull String toolName) {
        return toolsByName.get(toolName);
    }

    @NonNull
    public Map<String, ToolMethodEntry> getTools() {
        return Collections.unmodifiableMap(toolsByName);
    }
}
