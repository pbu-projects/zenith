package lol.pbu.mcp;

import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
public class SampleReactiveToolBean {

    @Tool(name = "test_echo", description = "Echoes back a payload asynchronously")
    public Mono<Map<String, Object>> testEcho(@ToolArg(description = "Message to echo") String message) {
        return Mono.just(Map.of("received", message, "status", "ok"));
    }

    @Tool(name = "test_error", description = "Fails with IllegalArgumentException")
    public Mono<String> testError() {
        return Mono.error(new IllegalArgumentException("Invalid reactive parameter"));
    }

    @Tool(name = "test_empty", description = "Returns empty Mono")
    public Mono<Void> testEmpty() {
        return Mono.empty();
    }

    @Tool(name = "test_future", description = "Returns CompletableFuture")
    public CompletableFuture<String> testFuture(@ToolArg(description = "Name") String name) {
        return CompletableFuture.completedFuture("Hello " + name);
    }
}
