package lol.pbu.tools;

import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper;
import io.modelcontextprotocol.spec.McpError;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback exception mapper for unhandled Throwables to prevent Micronaut MCP's internal
 * AbstractMcpMethodRegistry from crashing with "message must not be empty" when building McpError.
 */
@Singleton
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultThrowableMcpErrorMapper implements McpErrorExceptionMapper<Throwable> {

    private static final Logger log = LoggerFactory.getLogger(DefaultThrowableMcpErrorMapper.class);

    @Override
    public boolean canMap(Class<? extends Throwable> c) {
        return true;
    }

    @Override
    public McpError map(Throwable e) {
        log.error("Unhandled exception during MCP tool execution: {}", e.getMessage(), e);
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        return McpError.builder(-32603)
                .message(msg)
                .build();
    }
}
