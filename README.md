# Zenith

A high-performance **Model Context Protocol (MCP)** server for Zendesk, built with [Micronaut MCP](https://micronaut-projects.github.io/micronaut-mcp/latest/guide/#stdio) and Peanut Butter Unicorn's [z4j](https://github.com/pbu-projects/z4j) client library.

Zenith communicates over the **STDIO** transport, allowing Large Language Models (LLMs) and AI coding assistants (such as Claude Desktop, Cursor, and Antigravity) to manage, search, and automate Zendesk workspaces in real-time.

---

## What Can Be Done in This Release

This initial release delivers a production-ready Zendesk MCP server designed around Zendesk's upcoming API deprecations, complete with automatic OAuth 2.0 token management, dual-binary build capabilities (JVM and GraalVM Native), and an end-to-end MCP Client test suite.

### 1. Complete Zendesk Ticket Management & Search

Zenith exposes **8 MCP tools** implementing core Zendesk support capabilities:

#### 🔍 Search & Discovery
* **`search`**: Search Zendesk tickets, users, and organizations using native Zendesk query syntax (e.g. `type:ticket status:open priority:urgent`, `type:ticket created>2026-01-01`). Supports pagination (`page`, `perPage`).
* **`searchCount`**: Return the total number of items matching any Zendesk search query without downloading records.
* **`listTicketFields`**: Inspect all active ticket fields and dropdown options configured in the Zendesk account.

#### 🎫 Ticket Operations
* **`getTicket`**: Retrieve comprehensive details for a specific ticket by numeric ID.
* **`listTickets`**: Browse recent tickets across the instance.
* **`getTicketCount`**: Fetch aggregate ticket counts and cache refresh timestamps.
* **`createTicket`**: Open new tickets with subject, initial comment/body, priority (`urgent`, `high`, `normal`, `low`), and status (`new`, `open`, `pending`, `hold`, `solved`, `closed`).
* **`updateTicket`**: Add comments (either public replies or private internal notes), change ticket status, or update priority on an existing ticket.

---

## Complete Tool Reference

| Tool Name | Parameters | Type | Required | Description |
| :--- | :--- | :--- | :---: | :--- |
| **`search`** | `query`<br>`page`<br>`perPage` | `String`<br>`Integer`<br>`Integer` | **Yes**<br>No<br>No | Search Zendesk using Lucene-based syntax (default page: `1`, default perPage: `25`, max: `100`). |
| **`searchCount`** | `query` | `String` | **Yes** | Returns count of matching items for a given search query string. |
| **`getTicket`** | `ticketId` | `Long` | **Yes** | Fetches full ticket payload (subject, requester, priority, status, tags, custom fields). |
| **`listTickets`** | *(none)* | — | — | Lists recent tickets from the instance. |
| **`getTicketCount`** | *(none)* | — | — | Returns ticket metrics (total tickets, refreshed timestamp). |
| **`createTicket`** | `subject`<br>`comment`<br>`priority`<br>`status` | `String`<br>`String`<br>`String`<br>`String` | **Yes**<br>**Yes**<br>No<br>No | Creates a new Zendesk ticket. Valid priorities: `urgent`, `high`, `normal`, `low`. Valid statuses: `new`, `open`, `pending`, `hold`, `solved`, `closed`. |
| **`updateTicket`** | `ticketId`<br>`comment`<br>`status`<br>`priority`<br>`isPublic` | `Long`<br>`String`<br>`String`<br>`String`<br>`Boolean` | **Yes**<br>No<br>No<br>No<br>No | Updates an existing ticket. `isPublic: false` creates an internal agent note; `isPublic: true` creates a public customer reply. |
| **`listTicketFields`** | *(none)* | — | — | Lists active custom ticket fields, field keys, and tagger options. |

---

## Authentication: Built for the OAuth Era

Zendesk is deprecating static HTTP Basic Auth API tokens (`email/token:api_token`) in favor of scoped, revocable **OAuth 2.0 Bearer tokens**. 

Zenith is architected specifically for this transition:

* **Confidential Client Credentials Grant**: Zenith authenticates via `POST /oauth/tokens` using your `client_id` and `client_secret`.
* **Automatic Lifecycle Management**: The server automatically requests the token, caches it in memory, and seamlessly renews it prior to its 30-minute expiration without interrupting tool executions or prompting the user.
* **Static Bearer Token Fallback**: If an existing OAuth Bearer access token is provided via `ZENDESK_OAUTH_TOKEN`, Zenith will use it directly.
* **Zero Identity Transmission**: No email or password is sent over the wire; identity and authorization scopes are bound to the token directly on Zendesk's authorization servers.

---

## Zendesk Sandbox / Production Setup

To connect Zenith to your Zendesk account:

1. Log in to your Zendesk instance as an Admin (`https://<subdomain>.zendesk.com/admin`).
2. In the sidebar, navigate to **Apps and integrations** > **APIs** > **Zendesk API**.
3. Select the **OAuth Clients** tab and click **Add OAuth client**:
   * **Client Name**: `Zenith MCP Server`
   * **Client Kind**: **Confidential** *(Crucial: allows machine-to-machine Client Credentials grant)*
   * **Unique Identifier**: `zenith_development` *(This will be your `client_id`)*
   * **Redirect URLs**: `http://localhost`
4. Click **Save** and **copy the Secret immediately** (Zendesk will only display it once).

---

## Configuration

Set environment variables or create a `.env` file in the project root (see `.env.example`):

```bash
# Required for Confidential Client Credentials
export ZENDESK_URL="https://<your-subdomain>.zendesk.com"
export ZENDESK_CLIENT_ID="zenith_development"
export ZENDESK_CLIENT_SECRET="<your_oauth_client_secret>"
export ZENDESK_OAUTH_SCOPE="read write"

# Optional: Direct Static Bearer Token Override
# export ZENDESK_OAUTH_TOKEN="<your_bearer_token>"
```

---

## Building and Packaging

Zenith provides two compilation targets:

### 1. JVM Application Distribution
Builds a standard cross-platform JVM application distribution:
```bash
./gradlew installDist
```
The executable startup script is created at:
```text
build/install/zenith/bin/zenith
```

### 2. GraalVM Standalone Native Executable (~40ms Startup)
Compiles a self-contained native machine binary using GraalVM Ahead-Of-Time (AOT) compilation:
```bash
./gradlew nativeCompile
```
The standalone executable is created at:
```text
build/native/nativeCompile/zenith
```
* **Instant Startup**: Boots in **~38–40 milliseconds**.
* **Zero External Dependencies**: Does not require a local Java Runtime Environment (JRE) to run once compiled.
* **Minimal Memory Footprint**: Ideal for persistent background MCP daemon hosting.

---

## MCP Host Integration

### Claude Desktop
Add Zenith to your `claude_desktop_config.json`:

#### Option A: Standalone Native Binary (Recommended)
```json
{
  "mcpServers": {
    "zendesk": {
      "command": "/path/to/zenith/build/native/nativeCompile/zenith",
      "env": {
        "ZENDESK_URL": "https://<your-subdomain>.zendesk.com",
        "ZENDESK_CLIENT_ID": "zenith_development",
        "ZENDESK_CLIENT_SECRET": "<your_client_secret>",
        "ZENDESK_OAUTH_SCOPE": "read write"
      }
    }
  }
}
```

#### Option B: JVM Distribution
```json
{
  "mcpServers": {
    "zendesk": {
      "command": "/path/to/zenith/build/install/zenith/bin/zenith",
      "env": {
        "ZENDESK_URL": "https://<your-subdomain>.zendesk.com",
        "ZENDESK_CLIENT_ID": "zenith_development",
        "ZENDESK_CLIENT_SECRET": "<your_client_secret>",
        "ZENDESK_OAUTH_SCOPE": "read write"
      }
    }
  }
}
```

### Cursor / Antigravity / Windsurf
Add to your project or global MCP settings:
```json
{
  "mcpServers": {
    "zendesk": {
      "command": "/path/to/zenith/build/native/nativeCompile/zenith",
      "env": {
        "ZENDESK_URL": "https://<your-subdomain>.zendesk.com",
        "ZENDESK_CLIENT_ID": "zenith_development",
        "ZENDESK_CLIENT_SECRET": "<your_client_secret>"
      }
    }
  }
}
```

---

## Testing & Quality Assurance

Zenith features comprehensive test verification:

* **End-to-End MCP Client Test Suite (`ZendeskMcpClientSpec.groovy`)**:
  Spawns Zenith as an external process and connects using the official Java MCP Client SDK (`io.modelcontextprotocol.sdk:mcp-core`). Validates handshake, server initialization, tool discovery, and tool execution over STDIO.
* **Live Sandbox Verification (`ZenithSpec.groovy`)**:
  Verifies reactive API calls through `z4j` against a live Zendesk sandbox instance using OAuth token negotiation.
* **Strict STDIO Hygiene**:
  Logback status listeners are silenced and logs are strictly redirected to `stderr`, guaranteeing that `stdout` is never corrupted by logging frames.

Run the test suite:
```bash
./gradlew test
```

---

## Technology Stack

* **Language**: Java 25
* **Framework**: [Micronaut 5.0](https://micronaut.io)
* **MCP Server**: `io.micronaut.mcp:micronaut-mcp-server-java-sdk`
* **MCP Client (Testing)**: `io.modelcontextprotocol.sdk:mcp-core` / `micronaut-mcp-client-java-sdk`
* **Zendesk Client**: [lol.pbu:z4j:0.2.1](https://github.com/pbu-projects/z4j)
* **AOT & Native**: GraalVM Native Image
* **Testing**: Spock 2 with Groovy
