package lol.pbu.tools;

import io.micronaut.core.annotation.Order;
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper;
import io.modelcontextprotocol.spec.McpError;
import jakarta.inject.Singleton;

/**
 * Maps IllegalArgumentException to an MCP error with code -32602 (Invalid params)
 * and preserves the descriptive validation message so clients and LLM agents receive actionable feedback.
 */
@Singleton
@Order(100)
public class IllegalArgumentExceptionMcpErrorMapper implements McpErrorExceptionMapper<IllegalArgumentException> {

    @Override
    public boolean canMap(Class<? extends Throwable> c) {
        return IllegalArgumentException.class.isAssignableFrom(c);
    }

    @Override
    public McpError map(IllegalArgumentException e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "Invalid argument";
        }
        return McpError.builder(-32602)
                .message(msg)
                .build();
    }
}
