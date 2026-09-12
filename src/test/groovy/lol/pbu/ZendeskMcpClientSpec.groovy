package lol.pbu

import io.micronaut.mcp.server.json.MicronautMcpJsonMapperSupplier
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.client.transport.ServerParameters
import io.modelcontextprotocol.client.transport.StdioClientTransport
import io.modelcontextprotocol.spec.McpSchema
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.nio.file.Paths
import java.time.Duration

/**
 * End-to-end specification verifying Zenith as an STDIO MCP server
 * using the official MCP Client Java SDK.
 */
@Stepwise
class ZendeskMcpClientSpec extends Specification {

    @Shared
    McpSyncClient mcpClient

    @Shared
    String binaryPath

    def setupSpec() {
        def isWindows = System.getProperty("os.name", "").toLowerCase().contains("win")
        binaryPath = Paths.get(isWindows ? "build/install/zenith/bin/zenith.bat" : "build/install/zenith/bin/zenith").toAbsolutePath().toString()
        def envMap = new HashMap<String, String>()

        // Load credentials from local .env if present
        def envFile = new File(".env")
        if (envFile.exists()) {
            envFile.eachLine { line ->
                line = line.trim()
                if (line && !line.startsWith("#") && line.contains("=")) {
                    def parts = line.split("=", 2)
                    envMap.put(parts[0].trim(), parts[1].trim())
                }
            }
        }

        // Add any process environment or system property overrides
        ["ZENDESK_URL", "ZENDESK_CLIENT_ID", "ZENDESK_CLIENT_SECRET", "ZENDESK_OAUTH_TOKEN", "ZENDESK_OAUTH_SCOPE"].each { key ->
            def val = System.getenv(key) ?: System.getProperty(key)
            if (val) {
                envMap.put(key, val)
            }
        }

        // Enforce that live Zendesk environment credentials are configured
        def zendeskUrl = envMap.get("ZENDESK_URL")
        def hasAuth = (envMap.get("ZENDESK_CLIENT_ID") && envMap.get("ZENDESK_CLIENT_SECRET")) || envMap.get("ZENDESK_OAUTH_TOKEN")
        if (!zendeskUrl || !hasAuth) {
            throw new IllegalStateException("A live Zendesk sandbox environment is strictly required for tests. Please configure ZENDESK_URL and credentials in .env or environment variables.")
        }

        def serverParams = ServerParameters.builder(binaryPath)
                .env(envMap)
                .build()

        def jsonMapper = new MicronautMcpJsonMapperSupplier().get()
        def transport = new StdioClientTransport(serverParams, jsonMapper)
        transport.setStdErrorHandler({ line -> System.err.println("[SERVER-STDERR] " + line) })
        def validator = [
                validate: { Map<String, Object> schema, Object value ->
                    io.modelcontextprotocol.json.schema.JsonSchemaValidator.ValidationResponse.asValid(null)
                }
        ] as io.modelcontextprotocol.json.schema.JsonSchemaValidator

        mcpClient = McpClient.sync(transport)
                .jsonSchemaValidator(validator)
                .requestTimeout(Duration.ofSeconds(30))
                .build()
    }

    def cleanupSpec() {
        if (mcpClient != null) {
            try {
                mcpClient.closeGracefully()
            } catch (Exception ignored) {}
        }
    }

    def "1. MCP Client connects and initializes with the STDIO MCP server"() {
        when: "the MCP client sends an initialize request"
        def initResult = mcpClient.initialize()

        then: "initialization succeeds and returns server metadata"
        initResult != null
        initResult.serverInfo != null
        initResult.serverInfo.name == "zenith-zendesk-mcp"
        initResult.serverInfo.version == "0.0.3"
        initResult.capabilities != null
        initResult.capabilities.tools != null
    }

    def "2. MCP Client lists available tools exposed by the server"() {
        when: "the MCP client requests the list of tools"
        def toolsResult = mcpClient.listTools()

        then: "all exposed Zendesk tools are registered and described"
        toolsResult != null
        def toolNames = toolsResult.tools*.name
        toolNames.containsAll([
                "getTicket",
                "getTickets",
                "listTickets",
                "getTicketCount",
                "search",
                "searchCount",
                "createTicket",
                "updateTicket",
                "listTicketFields",
                "getTicketField",
                "listTicketForms",
                "getTicketForm",
                "listCustomObjects",
                "getCustomObject",
                "listCustomObjectRecords",
                "getCustomObjectRecord",
                "searchCustomObjectRecords",
                "uploadAttachment",
                "batchUpdateTickets",
                "getJobStatus",
                "getTicketAudits",
                "getToolVersion"
        ])
    }

    def "3. MCP Client invokes getTicketCount tool over STDIO protocol"() {
        when: "client invokes getTicketCount tool"
        def result = mcpClient.callTool(new McpSchema.CallToolRequest("getTicketCount", [:]))

        then: "the tool call returns successfully"
        result != null
        !Boolean.TRUE.equals(result.isError())
        result.content() != null
        !result.content().isEmpty()
    }

    def "4. MCP Client invokes search tool over STDIO protocol"() {
        when: "client searches tickets using Zendesk search syntax with sideloading"
        def result = mcpClient.callTool(new McpSchema.CallToolRequest("search", [
                query: "type:ticket",
                include: "users,organizations",
                perPage: 2
        ]))

        then: "search results are returned over the MCP protocol"
        result != null
        !Boolean.TRUE.equals(result.isError())
        result.content() != null
        !result.content().isEmpty()
    }

    def "5. MCP Client invokes listTicketFields tool over STDIO protocol"() {
        when: "client requests ticket fields schema"
        def result = mcpClient.callTool(new McpSchema.CallToolRequest("listTicketFields", [:]))

        then: "fields are returned"
        result != null
        !Boolean.TRUE.equals(result.isError())
        result.content() != null
        !result.content().isEmpty()
    }

    def "6. MCP Client invokes getTickets tool over STDIO protocol"() {
        when: "client invokes getTickets tool with a list of ticket IDs"
        def result = mcpClient.callTool(new McpSchema.CallToolRequest("getTickets", [
                ticketIds: [7L]
        ]))

        then: "tickets are returned over the MCP protocol"
        result != null
        !Boolean.TRUE.equals(result.isError())
        result.content() != null
        !result.content().isEmpty()
    }

    def "7. MCP Client invokes listTicketForms tool over STDIO protocol"() {
        when: "client invokes listTicketForms tool"
        def result = mcpClient.callTool(new McpSchema.CallToolRequest("listTicketForms", [:]))

        then: "ticket forms are returned over the MCP protocol"
        result != null
        !Boolean.TRUE.equals(result.isError())
        result.content() != null
        !result.content().isEmpty()
    }

    def "8. MCP Client invokes listCustomObjects tool over STDIO protocol"() {
        when: "client invokes listCustomObjects tool"
        def result = mcpClient.callTool(new McpSchema.CallToolRequest("listCustomObjects", [:]))

        then: "custom objects are returned over the MCP protocol"
        result != null
        !Boolean.TRUE.equals(result.isError())
        result.content() != null
        !result.content().isEmpty()
    }

    def "9. MCP Client invokes uploadAttachment tool over STDIO protocol"() {
        given: "a temporary file on disk"
        File tempFile = File.createTempFile("mcp-upload-", ".txt")
        tempFile.text = "MCP client attachment upload test"

        when: "client calls uploadAttachment tool"
        def result = mcpClient.callTool(new McpSchema.CallToolRequest("uploadAttachment", [
                filePath: tempFile.absolutePath,
                filename: "mcp-attachment.txt"
        ]))

        then: "upload succeeds and returns upload token"
        result != null
        !Boolean.TRUE.equals(result.isError())
        result.content() != null
        !result.content().isEmpty()

        cleanup:
        tempFile?.delete()
    }

    def "10. MCP Client invokes batchUpdateTickets tool over STDIO protocol"() {
        when: "client calls batchUpdateTickets tool"
        def result = mcpClient.callTool(new McpSchema.CallToolRequest("batchUpdateTickets", [
                ticketIds: [7L],
                comment: "STDIO batch update test comment",
                isPublic: false
        ]))

        then: "batch update completes and returns result list"
        result != null
        !Boolean.TRUE.equals(result.isError())
        result.content() != null
        !result.content().isEmpty()
    }

    def "11. MCP Client invokes getTicketAudits tool over STDIO protocol"() {
        when: "client calls getTicketAudits tool for ticket event history"
        def result = mcpClient.callTool(new McpSchema.CallToolRequest("getTicketAudits", [
                ticketId: 7L
        ]))

        then: "ticket audits are returned over MCP protocol"
        result != null
        !Boolean.TRUE.equals(result.isError())
        result.content() != null
        !result.content().isEmpty()
    }
}
