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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

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

        String msg = buildErrorMessage(e);
        log.error("Unhandled exception during MCP tool execution: {}", msg, e);
        return McpError.builder(-32603)
                .message(msg)
                .build();
    }

    public static String buildErrorMessage(Throwable e) {
        if (e == null) {
            return "Unknown error";
        }
        StringBuilder sb = new StringBuilder();
        String primaryMsg = e.getMessage();
        String simpleName = e.getClass().getSimpleName();
        if (simpleName.isBlank()) {
            simpleName = e.getClass().getName();
        }
        if (primaryMsg != null && !primaryMsg.isBlank()) {
            sb.append(primaryMsg.trim());
        } else {
            sb.append(simpleName);
        }

        if (e.getCause() != null) {
            String causeChain = formatCauseChain(e.getCause());
            if (!causeChain.isBlank()) {
                sb.append("; Caused by: ").append(causeChain);
            }
        }
        return sb.toString();
    }

    public static String formatCauseChain(Throwable cause) {
        if (cause == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = cause;
        int depth = 0;
        while (current != null && depth < 10 && seen.add(current)) {
            String causeName = current.getClass().getSimpleName();
            if (causeName.isBlank()) {
                causeName = current.getClass().getName();
            }
            String causeMsg = current.getMessage();
            if (!sb.isEmpty()) {
                sb.append("; Caused by: ");
            }
            sb.append(causeName);
            if (causeMsg != null && !causeMsg.isBlank()) {
                sb.append(": ").append(causeMsg.trim());
            }
            current = current.getCause();
            depth++;
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T findCause(Throwable t, Class<T> targetClass) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = t;
        int depth = 0;
        while (current != null && depth < 20 && seen.add(current)) {
            if (targetClass.isInstance(current)) {
                return (T) current;
            }
            current = current.getCause();
            depth++;
        }
        return null;
    }
}
