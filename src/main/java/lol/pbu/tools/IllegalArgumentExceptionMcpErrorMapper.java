package lol.pbu.tools;

import io.micronaut.core.annotation.Order;
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper;
import io.modelcontextprotocol.spec.McpError;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps IllegalArgumentException to an MCP error with code -32602 (Invalid params)
 * and preserves the descriptive validation message so clients and LLM agents receive actionable feedback.
 */
@Singleton
@Order(100)
public class IllegalArgumentExceptionMcpErrorMapper implements McpErrorExceptionMapper<IllegalArgumentException> {

    private static final Logger log = LoggerFactory.getLogger(IllegalArgumentExceptionMcpErrorMapper.class);

    @Override
    public boolean canMap(Class<? extends Throwable> c) {
        return IllegalArgumentException.class.isAssignableFrom(c);
    }

    @Override
    public McpError map(IllegalArgumentException e) {
        String msg = e.getMessage();
        if (e.getCause() != null) {
            msg = DefaultThrowableMcpErrorMapper.buildErrorMessage(e);
        } else if (msg == null || msg.isBlank()) {
            msg = "Invalid argument";
        }
        log.error("Unhandled IllegalArgumentException during MCP tool execution: {}", msg, e);
        return McpError.builder(-32602)
                .message(msg)
                .build();
    }
}
