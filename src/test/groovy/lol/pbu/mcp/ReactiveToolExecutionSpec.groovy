package lol.pbu.mcp

import io.micronaut.context.ApplicationContext
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import io.modelcontextprotocol.server.McpAsyncServer
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification
import io.modelcontextprotocol.spec.McpError
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.TextContent
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class ReactiveToolExecutionSpec extends Specification {

    def "McpAsyncServer executes reactive tool returning Mono without blocking"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
                .packages("lol.pbu.mcp")
                .start()
        ReactiveToolMethodRegistry registry = context.getBean(ReactiveToolMethodRegistry)
        McpAsyncServer server = context.getBean(McpAsyncServer)

        when: "discovering registered tool methods"
        def echoEntry = registry.find("test_echo")
        def errorEntry = registry.find("test_error")
        def emptyEntry = registry.find("test_empty")
        def futureEntry = registry.find("test_future")

        then: "all tool methods are indexed"
        echoEntry != null
        errorEntry != null
        emptyEntry != null
        futureEntry != null

        when: "inspecting McpAsyncServer tools"
        def serverToolsField = McpAsyncServer.getDeclaredField("tools")
        serverToolsField.setAccessible(true)
        List<AsyncToolSpecification> specs = (List<AsyncToolSpecification>) serverToolsField.get(server)
        def echoSpec = specs.find { it.tool().name() == "test_echo" }
        def errorSpec = specs.find { it.tool().name() == "test_error" }
        def emptySpec = specs.find { it.tool().name() == "test_empty" }
        def futureSpec = specs.find { it.tool().name() == "test_future" }

        then: "tool specifications are present on server"
        echoSpec != null
        errorSpec != null
        emptySpec != null
        futureSpec != null

        when: "invoking test_echo tool"
        CallToolRequest echoReq = new CallToolRequest("test_echo", Map.of("message", "reactive-world"))
        Mono<CallToolResult> echoMono = echoSpec.callHandler().apply(null, echoReq)
        CallToolResult echoResult = echoMono.block()

        then: "echo returns structured or text content matching the payload"
        echoResult != null
        !echoResult.isError()
        echoResult.content() != null
        echoResult.content().size() == 1
        TextContent content = (TextContent) echoResult.content().get(0)
        content.text().contains("reactive-world")
        content.text().contains("ok")

        when: "invoking test_future tool"
        CallToolRequest futureReq = new CallToolRequest("test_future", Map.of("name", "Graeme"))
        Mono<CallToolResult> futureMono = futureSpec.callHandler().apply(null, futureReq)
        CallToolResult futureResult = futureMono.block()

        then: "completable future resolves correctly"
        futureResult != null
        !futureResult.isError()
        TextContent futureContent = (TextContent) futureResult.content().get(0)
        futureContent.text().contains("Hello Graeme")

        when: "invoking test_empty tool"
        CallToolRequest emptyReq = new CallToolRequest("test_empty", Collections.emptyMap())
        Mono<CallToolResult> emptyMono = emptySpec.callHandler().apply(null, emptyReq)
        CallToolResult emptyResult = emptyMono.block()

        then: "empty Mono completes cleanly with empty result"
        emptyResult != null
        !emptyResult.isError()

        when: "invoking test_error tool"
        CallToolRequest errorReq = new CallToolRequest("test_error", Collections.emptyMap())
        Mono<CallToolResult> errorMono = errorSpec.callHandler().apply(null, errorReq)
        errorMono.block()

        then: "IllegalArgumentException is mapped to McpError"
        def ex = thrown(McpError)
        ex.message.contains("Invalid reactive parameter")

        cleanup:
        context?.close()
    }
}
