package lol.pbu.mcp

import io.micronaut.context.ApplicationContext
import io.micronaut.core.bind.ArgumentBinderRegistry
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper
import io.micronaut.mcp.server.registry.ToolRegistry
import io.modelcontextprotocol.server.McpAsyncServer
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification
import io.modelcontextprotocol.spec.McpError
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.TextContent
import io.modelcontextprotocol.spec.McpSchema.Tool
import reactor.core.publisher.Mono
import spock.lang.Specification

class ReactiveToolExecutionSpec extends Specification {

    def "McpAsyncServer executes reactive tool returning Mono without blocking"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
                .packages("lol.pbu.mcp")
                .start()
        ReactiveToolMethodRegistry registry = context.getBean(ReactiveToolMethodRegistry)
        McpAsyncServer server = context.getBean(McpAsyncServer)
        ToolRegistry toolRegistry = context.getBean(ToolRegistry)
        ArgumentBinderRegistry<CallToolRequest> binderRegistry = (ArgumentBinderRegistry<CallToolRequest>) context.getBean(Argument.of(ArgumentBinderRegistry, CallToolRequest))
        JsonMapper jsonMapper = context.getBean(JsonMapper)
        List<McpErrorExceptionMapper<? extends Throwable>> exceptionMappers = (List<McpErrorExceptionMapper<? extends Throwable>>) context.getBeansOfType(McpErrorExceptionMapper)
        JsonSchemaClassPathResourceLoader schemaLoader = context.findBean(JsonSchemaClassPathResourceLoader).orElse(null)

        when: "discovering registered tool methods"
        def echoEntry = registry.find("test_echo")
        def errorEntry = registry.find("test_error")
        def emptyEntry = registry.find("test_empty")
        def futureEntry = registry.find("test_future")
        def rateLimitEntry = registry.find("test_rate_limit")

        then: "all tool methods are indexed"
        echoEntry != null
        errorEntry != null
        emptyEntry != null
        futureEntry != null
        rateLimitEntry != null

        when: "listing tools via McpAsyncServer public API"
        List<Tool> serverTools = server.listTools().collectList().block()
        def echoTool = serverTools.find { it.name() == "test_echo" }
        def errorTool = serverTools.find { it.name() == "test_error" }
        def emptyTool = serverTools.find { it.name() == "test_empty" }
        def futureTool = serverTools.find { it.name() == "test_future" }
        def rateLimitTool = serverTools.find { it.name() == "test_rate_limit" }

        then: "tools are registered on McpAsyncServer"
        echoTool != null
        errorTool != null
        emptyTool != null
        futureTool != null
        rateLimitTool != null

        when: "building async tool specifications via factory without reflection"
        List<AsyncToolSpecification> rawSpecs = toolRegistry.getAsyncSpecs()
        def echoSpec = ZenithMcpAsyncServerFactory.createAsyncToolSpecification(rawSpecs.find { it.tool().name() == "test_echo" }, echoEntry, binderRegistry, jsonMapper, context, exceptionMappers, schemaLoader)
        def errorSpec = ZenithMcpAsyncServerFactory.createAsyncToolSpecification(rawSpecs.find { it.tool().name() == "test_error" }, errorEntry, binderRegistry, jsonMapper, context, exceptionMappers, schemaLoader)
        def emptySpec = ZenithMcpAsyncServerFactory.createAsyncToolSpecification(rawSpecs.find { it.tool().name() == "test_empty" }, emptyEntry, binderRegistry, jsonMapper, context, exceptionMappers, schemaLoader)
        def futureSpec = ZenithMcpAsyncServerFactory.createAsyncToolSpecification(rawSpecs.find { it.tool().name() == "test_future" }, futureEntry, binderRegistry, jsonMapper, context, exceptionMappers, schemaLoader)
        def rateLimitSpec = ZenithMcpAsyncServerFactory.createAsyncToolSpecification(rawSpecs.find { it.tool().name() == "test_rate_limit" }, rateLimitEntry, binderRegistry, jsonMapper, context, exceptionMappers, schemaLoader)

        and: "invoking test_echo tool"
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

        when: "invoking test_rate_limit tool"
        CallToolRequest rateLimitReq = new CallToolRequest("test_rate_limit", Collections.emptyMap())
        Mono<CallToolResult> rateLimitMono = rateLimitSpec.callHandler().apply(null, rateLimitReq)
        rateLimitMono.block()

        then: "HTTP 429 is mapped to McpError with code -32029 and next allowable call context"
        def rateLimitEx = thrown(McpError)
        rateLimitEx.jsonRpcError.code == -32029
        rateLimitEx.message.contains("Zendesk API rate limit exceeded (HTTP 429 Too Many Requests)")
        rateLimitEx.message.contains("30 seconds")
        rateLimitEx.message.contains("Next allowable call at:")

        cleanup:
        context?.close()
    }
}
