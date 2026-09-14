package lol.pbu.tools;

import io.micronaut.context.annotation.Value;
import io.micronaut.mcp.annotations.Tool;
import jakarta.inject.Singleton;

@Singleton
public class SystemTools {

    @Value("${micronaut.mcp.server.info.version}")
    String serverVersion;

    @Tool(description = "Get the version of the MCP server")
    public String getToolVersion() {
        return serverVersion;
    }
}
