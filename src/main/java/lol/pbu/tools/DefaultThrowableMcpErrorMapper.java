package lol.pbu.tools;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper;
import io.modelcontextprotocol.spec.McpError;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback exception mapper for unhandled Throwables to prevent Micronaut MCP's internal
 * AbstractMcpMethodRegistry from crashing with "message must not be empty" when building McpError.
 * Also unwraps causal chains (e.g. RuntimeException wrapping HttpClientResponseException or
 * IllegalArgumentException) so that specialized error mappers can surface rich diagnostics.
 */
@Singleton
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultThrowableMcpErrorMapper implements McpErrorExceptionMapper<Throwable> {

    private static final Logger log = LoggerFactory.getLogger(DefaultThrowableMcpErrorMapper.class);

    private final HttpClientResponseExceptionMcpErrorMapper httpMapper;
    private final IllegalArgumentExceptionMcpErrorMapper illegalArgMapper;

    public DefaultThrowableMcpErrorMapper() {
        this(null, null);
    }

    @Inject
    public DefaultThrowableMcpErrorMapper(
            @Nullable HttpClientResponseExceptionMcpErrorMapper httpMapper,
            @Nullable IllegalArgumentExceptionMcpErrorMapper illegalArgMapper
    ) {
        this.httpMapper = httpMapper;
        this.illegalArgMapper = illegalArgMapper;
    }

    @Override
    public boolean canMap(Class<? extends Throwable> c) {
        return true;
    }

    @Override
    public McpError map(Throwable e) {
        HttpClientResponseException httpEx = findCause(e, HttpClientResponseException.class);
        if (httpEx != null && httpMapper != null) {
            log.info("Delegating wrapped HttpClientResponseException to HttpClientResponseExceptionMcpErrorMapper");
            return httpMapper.map(httpEx);
        }

        IllegalArgumentException argEx = findCause(e, IllegalArgumentException.class);
        if (argEx != null && illegalArgMapper != null) {
            log.info("Delegating wrapped IllegalArgumentException to IllegalArgumentExceptionMcpErrorMapper");
            return illegalArgMapper.map(argEx);
        }

        log.error("Unhandled exception during MCP tool execution: {}", e.getMessage(), e);
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        return McpError.builder(-32603)
                .message(msg)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T findCause(Throwable t, Class<T> targetClass) {
        Throwable current = t;
        while (current != null) {
            if (targetClass.isInstance(current)) {
                return (T) current;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }
}
