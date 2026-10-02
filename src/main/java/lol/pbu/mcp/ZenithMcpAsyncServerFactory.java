package lol.pbu.mcp;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.json.JsonMapper;
import io.micronaut.jsonschema.utils.JsonSchemaClassPathResourceLoader;
import io.micronaut.mcp.conf.server.McpServerInfoConfiguration;
import io.micronaut.mcp.conf.server.PromptsConfiguration;
import io.micronaut.mcp.conf.server.ResourcesConfiguration;
import io.micronaut.mcp.conf.server.ToolsConfiguration;
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper;
import io.micronaut.mcp.server.registry.CompletionRegistry;
import io.micronaut.mcp.server.registry.PromptRegistry;
import io.micronaut.mcp.server.registry.ResourceRegistry;
import io.micronaut.mcp.server.registry.ResourceTemplateRegistry;
import io.micronaut.mcp.server.registry.ToolRegistry;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.AsyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncCompletionSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceTemplateSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Replaces default Micronaut MCP async server factory to provide genuine non-blocking
 * reactive tool dispatching for tools returning {@link Publisher}, {@link Mono}, or {@link CompletableFuture}.
 */
@Factory
@Requires(property = "micronaut.mcp.server.reactive", value = "true", defaultValue = "true")
public class ZenithMcpAsyncServerFactory {

    private static final Logger log = LoggerFactory.getLogger(ZenithMcpAsyncServerFactory.class);

    @Singleton
    @Replaces(McpAsyncServer.class)
    @SuppressWarnings("java:S107")
    public McpAsyncServer createMcpAsyncServer(
            McpServerTransportProvider transportProvider,
            McpJsonMapper mcpJsonMapper,
            JsonSchemaValidator jsonSchemaValidator,
            @Nullable McpServerInfoConfiguration serverInfoConfiguration,
            ToolsConfiguration toolsConfiguration,
            PromptsConfiguration promptsConfiguration,
            ResourcesConfiguration resourcesConfiguration,
            ServerCapabilities.Builder capabilitiesBuilder,
            Provider<ServerCapabilities> capabilitiesProvider,
            ToolRegistry toolRegistry,
            PromptRegistry promptRegistry,
            ResourceRegistry resourceRegistry,
            ResourceTemplateRegistry resourceTemplateRegistry,
            CompletionRegistry completionRegistry,
            List<AsyncToolSpecification> extraTools,
            List<AsyncCompletionSpecification> extraCompletions,
            List<AsyncPromptSpecification> extraPrompts,
            List<AsyncResourceSpecification> extraResources,
            List<AsyncResourceTemplateSpecification> extraResourceTemplates,
            ReactiveToolMethodRegistry reactiveToolMethodRegistry,
            ArgumentBinderRegistry<CallToolRequest> argumentBinderRegistry,
            JsonMapper jsonMapper,
            BeanContext beanContext,
            List<McpErrorExceptionMapper<? extends Throwable>> exceptionMappers,
            @Nullable JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader
    ) {
        List<AsyncToolSpecification> rawSpecs = toolRegistry.getAsyncSpecs();
        List<AsyncToolSpecification> reactiveTools = new ArrayList<>(rawSpecs.size());

        for (AsyncToolSpecification rawSpec : rawSpecs) {
            String name = rawSpec.tool().name();
            ToolMethodEntry entry = reactiveToolMethodRegistry.find(name);
            if (entry != null) {
                reactiveTools.add(createAsyncToolSpecification(
                        rawSpec,
                        entry,
                        argumentBinderRegistry,
                        jsonMapper,
                        beanContext,
                        exceptionMappers,
                        jsonSchemaClassPathResourceLoader
                ));
            } else {
                reactiveTools.add(rawSpec);
            }
        }

        List<AsyncToolSpecification> allTools = CollectionUtils.concat(extraTools, reactiveTools);
        List<AsyncPromptSpecification> allPrompts = CollectionUtils.concat(extraPrompts, promptRegistry.getAsyncSpecs());
        List<AsyncResourceSpecification> allResources = CollectionUtils.concat(extraResources, resourceRegistry.getAsyncSpecs());
        List<AsyncResourceTemplateSpecification> allResourceTemplates = CollectionUtils.concat(extraResourceTemplates, resourceTemplateRegistry.getAsyncSpecs());
        List<AsyncCompletionSpecification> allCompletions = CollectionUtils.concat(extraCompletions, completionRegistry.getAsyncSpecs());

        if (!allTools.isEmpty()) {
            capabilitiesBuilder.tools(toolsConfiguration.isListChanged());
        }
        if (!allPrompts.isEmpty()) {
            capabilitiesBuilder.prompts(promptsConfiguration.isListChanged());
        }
        if (!allResources.isEmpty() || !allResourceTemplates.isEmpty()) {
            capabilitiesBuilder.resources(resourcesConfiguration.isSubscribe(), resourcesConfiguration.isListChanged());
        }
        if (!allCompletions.isEmpty()) {
            capabilitiesBuilder.completions();
        }

        ServerCapabilities capabilities = capabilitiesProvider.get();

        AsyncSpecification<?> spec = McpServer.async(transportProvider)
                .jsonMapper(mcpJsonMapper)
                .jsonSchemaValidator(jsonSchemaValidator)
                .capabilities(capabilities)
                .tools(allTools)
                .prompts(allPrompts)
                .resources(allResources)
                .resourceTemplates(allResourceTemplates)
                .completions(allCompletions);

        if (serverInfoConfiguration != null) {
            spec.serverInfo(serverInfoConfiguration.getName(), serverInfoConfiguration.getVersion());
        }

        log.info("Initialized reactive McpAsyncServer with {} non-blocking tools", allTools.size());
        return spec.build();
    }

    @SuppressWarnings("java:S107")
    static AsyncToolSpecification createAsyncToolSpecification(
            AsyncToolSpecification rawSpec,
            ToolMethodEntry entry,
            ArgumentBinderRegistry<CallToolRequest> argumentBinderRegistry,
            JsonMapper jsonMapper,
            BeanContext beanContext,
            List<McpErrorExceptionMapper<? extends Throwable>> exceptionMappers,
            @Nullable JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader
    ) {
        return new AsyncToolSpecification(
                rawSpec.tool(),
                (exchange, request) -> invokeTool(
                        entry.beanDefinition(),
                        entry.method(),
                        exchange,
                        request,
                        argumentBinderRegistry,
                        jsonMapper,
                        beanContext,
                        exceptionMappers,
                        jsonSchemaClassPathResourceLoader
                )
        );
    }

    @SuppressWarnings({"java:S107", "unchecked"})
    private static <B> Mono<CallToolResult> invokeTool(
            BeanDefinition<?> rawBeanDefinition,
            ExecutableMethod<?, ?> rawMethod,
            McpAsyncServerExchange exchange,
            CallToolRequest request,
            ArgumentBinderRegistry<CallToolRequest> argumentBinderRegistry,
            JsonMapper jsonMapper,
            BeanContext beanContext,
            List<McpErrorExceptionMapper<? extends Throwable>> exceptionMappers,
            @Nullable JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader
    ) {
        try {
            BeanDefinition<B> beanDefinition = (BeanDefinition<B>) rawBeanDefinition;
            ExecutableMethod<B, ?> method = (ExecutableMethod<B, ?>) rawMethod;
            B bean = beanContext.getBean(beanDefinition);
            McpTransportContext transportContext = exchange != null ? exchange.transportContext() : null;

            List<Object> candidates = new ArrayList<>();
            if (transportContext != null) {
                candidates.add(transportContext);
            }
            if (request != null) {
                candidates.add(request);
            }
            CallToolRequest safeRequest = request != null ? request : new CallToolRequest("", Map.of());

            Map<Argument<?>, Object> boundVariables = prepareBoundVariables(method.getArguments(), candidates);
            DefaultExecutableBinder<CallToolRequest> binder = new DefaultExecutableBinder<>(boundVariables);
            BoundExecutable<B, ?> bound = binder.bind(method, argumentBinderRegistry, safeRequest);
            Object rawResult = bound.invoke(bean);

            return switch (rawResult) {
                case Publisher<?> publisher -> Mono.from(publisher)
                        .map(payload -> serializeResult(payload, method.getReturnType().asArgument(), jsonMapper, jsonSchemaClassPathResourceLoader))
                        .defaultIfEmpty(CallToolResult.builder().isError(false).build())
                        .onErrorResume(error -> Mono.error(mapException(error, exceptionMappers)));
                case CompletableFuture<?> cf -> Mono.fromFuture(cf)
                        .map(payload -> serializeResult(payload, method.getReturnType().asArgument(), jsonMapper, jsonSchemaClassPathResourceLoader))
                        .defaultIfEmpty(CallToolResult.builder().isError(false).build())
                        .onErrorResume(error -> Mono.error(mapException(error, exceptionMappers)));
                case null, default -> {
                    CallToolResult result = serializeResult(rawResult, method.getReturnType().asArgument(), jsonMapper, jsonSchemaClassPathResourceLoader);
                    yield Mono.just(result);
                }
            };
        } catch (Exception ex) {
            return Mono.error(mapException(ex, exceptionMappers));
        }
    }

    private static Map<Argument<?>, Object> prepareBoundVariables(Argument<?>[] arguments, List<Object> candidates) {
        Map<Argument<?>, Object> bound = HashMap.newHashMap(arguments.length);
        for (Argument<?> argument : arguments) {
            Class<?> argType = argument.getType();
            for (Object candidate : candidates) {
                if (candidate != null && argType.isInstance(candidate)) {
                    bound.put(argument, candidate);
                    break;
                }
            }
        }
        return bound;
    }

    private static CallToolResult serializeResult(
            Object payload,
            Argument<?> returnTypeArg,
            JsonMapper jsonMapper,
            @Nullable JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader
    ) {
        if (payload == null) {
            return CallToolResult.builder().isError(false).build();
        }
        if (payload instanceof CallToolResult callToolResult) {
            return callToolResult;
        }
        if (payload instanceof String str) {
            return CallToolResult.builder().addTextContent(str).isError(false).build();
        }
        if (payload.getClass().isEnum()) {
            return CallToolResult.builder().addTextContent(payload.toString()).isError(false).build();
        }
        if (hasOutputSchema(returnTypeArg, jsonSchemaClassPathResourceLoader)) {
            if (payload instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stringMap = (Map<String, Object>) map;
                return CallToolResult.builder().structuredContent(stringMap).isError(false).build();
            }
            try {
                String json = jsonMapper.writeValueAsString(payload);
                Map<String, Object> map = jsonMapper.readValue(json, Argument.mapOf(String.class, Object.class));
                return CallToolResult.builder().structuredContent(map).isError(false).build();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to serialize tool execution result", e);
            }
        }
        try {
            String json = jsonMapper.writeValueAsString(payload);
            return CallToolResult.builder().addTextContent(json).isError(false).build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize tool execution result", e);
        }
    }

    private static boolean hasOutputSchema(
            Argument<?> returnTypeArg,
            @Nullable JsonSchemaClassPathResourceLoader jsonSchemaClassPathResourceLoader
    ) {
        if (jsonSchemaClassPathResourceLoader == null) {
            return false;
        }
        Class<?> targetType = returnTypeArg.getType();
        if (Publisher.class.isAssignableFrom(targetType) || CompletableFuture.class.isAssignableFrom(targetType)) {
            Optional<Argument<?>> firstTypeVar = returnTypeArg.getFirstTypeVariable();
            if (firstTypeVar.isPresent()) {
                targetType = firstTypeVar.get().getType();
            }
        }
        return jsonSchemaClassPathResourceLoader.jsonSchemaStringForClass(targetType).isPresent();
    }

    private static Throwable mapException(
            Throwable error,
            List<McpErrorExceptionMapper<? extends Throwable>> exceptionMappers
    ) {
        if (error instanceof McpError) {
            return error;
        }
        if (exceptionMappers != null) {
            for (McpErrorExceptionMapper<? extends Throwable> mapper : exceptionMappers) {
                if (mapper.canMap(error.getClass())) {
                    @SuppressWarnings("unchecked")
                    McpErrorExceptionMapper<Throwable> rawMapper = (McpErrorExceptionMapper<Throwable>) mapper;
                    return rawMapper.map(error);
                }
            }
        }
        return McpError.builder(-32603)
                .message(error.getMessage() != null ? error.getMessage() : "Internal server error")
                .build();
    }
}
