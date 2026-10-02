package lol.pbu.mcp;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

public record ToolMethodEntry(
        BeanDefinition<?> beanDefinition,
        ExecutableMethod<?, ?> method
) {
}
