package lol.pbu.tools;

import io.micronaut.context.annotation.Value;

import jakarta.inject.Singleton;

@Singleton
public class SystemTools {

    @Value("${micronaut.mcp.server.info.version}")
    String serverVersion;

    @io.micronaut.mcp.annotations.Resource(
        name = "serverVersion",
        uri = "zenith://version",
        title = "Server Version",
        description = "Get the version of the MCP server"
    )
    public String getServerVersion() {
        return serverVersion;
    }
}
