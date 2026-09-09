# Zenith

A high-performance Model Context Protocol (MCP) server for Zendesk, built with [Micronaut MCP](https://micronaut-projects.github.io/micronaut-mcp/latest/guide/#stdio) and [z4j](https://github.com/pbu-projects/z4j).

Zenith communicates over the **STDIO** transport, allowing AI coding assistants and LLM hosts (such as Claude Desktop, Cursor, and Antigravity) to seamlessly interact with your Zendesk instance using modern **OAuth 2.0 Client Credentials** authentication.

---

## Features

- **OAuth 2.0 Authentication**: Supports modern Confidential Client Credentials grant (`POST /oauth/tokens`) with automatic in-memory token caching and lifecycle renewals, as well as static Bearer token fallbacks.
- **Built on `z4j`**: Uses Peanut Butter Unicorn's lightweight, reactive Zendesk client library.
- **MCP Tools**:
  - `getTicket`: Retrieve full details of any ticket by ID.
  - `listTickets`: Browse recent tickets.
  - `getTicketCount`: Fetch total ticket statistics.
  - `search`: Search tickets, users, and organizations using native Zendesk query syntax.
  - `searchCount`: Get the count of search results matching a query.
  - `createTicket`: Open new tickets with subject, description, priority, and status.
  - `updateTicket`: Add public or internal comments and update ticket status/priority.
  - `listTicketFields`: Discover custom ticket fields and form configurations.
- **Tested with MCP Client Java SDK**: Validated end-to-end using `io.modelcontextprotocol.sdk:mcp-core`.

---

## Configuration

Copy `.env.example` to `.env` or set environment variables:

```bash
export ZENDESK_URL="https://<your-subdomain>.zendesk.com"
export ZENDESK_CLIENT_ID="<your_oauth_client_id>"
export ZENDESK_CLIENT_SECRET="<your_oauth_client_secret>"
export ZENDESK_OAUTH_SCOPE="read write"
```

### Zendesk OAuth Setup
1. In Zendesk Admin Center, navigate to **Apps and integrations** > **APIs** > **Zendesk API** > **OAuth Clients**.
2. Click **Add OAuth client**.
3. Set **Client Kind** to **Confidential**.
4. Set Redirect URL to `http://localhost`.
5. Save and copy the **Client Secret** and **Unique Identifier** (`client_id`).

---

## Building and Running

### Build the JVM Distribution
```bash
./gradlew installDist
```
The executable binary will be generated at `build/install/zenith/bin/zenith`.

### Build the GraalVM Standalone Native Executable (~40ms startup)
```bash
./gradlew nativeCompile
```
The standalone native executable will be generated at `build/native/nativeCompile/zenith`.

### Running Tests
```bash
./gradlew test
```

---

## MCP Host Integration

To register Zenith with MCP clients (e.g. Claude Desktop, Cursor):

```json
{
  "mcpServers": {
    "zendesk": {
      "command": "/path/to/zenith/build/install/zenith/bin/zenith",
      "env": {
        "ZENDESK_URL": "https://<your-subdomain>.zendesk.com",
        "ZENDESK_CLIENT_ID": "<your_client_id>",
        "ZENDESK_CLIENT_SECRET": "<your_client_secret>",
        "ZENDESK_OAUTH_SCOPE": "read write"
      }
    }
  }
}
```
