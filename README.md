# Zenith

Zendesk STDIO MCP server.

## Installation

Download the binary for your platform from the [latest release](https://github.com/pbu-projects/zenith/releases/latest).

If no binary exists for your platform, see [BUILD.md](BUILD.md).

## Zendesk Setup

1. Open `https://<subdomain>.zendesk.com/admin`.
2. Go to **Apps and integrations** > **APIs** > **Zendesk API** > **OAuth Clients**.
3. Click **Add OAuth client**.
4. Set **Client Kind** to **Confidential**.
5. Set **Redirect URLs** to `http://localhost`.
6. Save and record **Unique Identifier** (`client_id`) and **Secret**.

## MCP Host Setup

Add to your MCP client config:

```json
{
  "mcpServers": {
    "zendesk": {
      "command": "/path/to/zenith",
      "env": {
        "ZENDESK_URL": "https://<subdomain>.zendesk.com",
        "ZENDESK_CLIENT_ID": "<client_id>",
        "ZENDESK_CLIENT_SECRET": "<client_secret>",
        "ZENDESK_OAUTH_SCOPE": "read write"
      }
    }
  }
}
```

## Tools

* `getTicket`: Fetch ticket by ID.
* `listTickets`: List recent tickets.
* `getTicketCount`: Fetch ticket counts.
* `search`: Search Zendesk (`type:ticket ...`).
* `searchCount`: Count search results.
* `createTicket`: Create ticket.
* `updateTicket`: Update ticket comment, status, or priority.
* `listTicketFields`: List ticket fields.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
