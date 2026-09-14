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
* `getTickets`: Fetch multiple tickets by IDs.
* `listTickets`: List recent tickets.
* `getTicketCount`: Fetch ticket counts.
* `search`: Search Zendesk (`type:ticket ...`). Supports sideloading related resources (`include=users,organizations,groups`).
* `searchCount`: Count search results.
* `createTicket`: Create ticket (supports subject, comment, status, priority, and attachments).
* `updateTicket`: Update ticket comment, status, priority, or attachments.
* `uploadAttachment`: Upload local file or image and receive an upload token to attach to tickets.
* `batchUpdateTickets`: Batch update multiple tickets concurrently or asynchronously via Zendesk bulk job.
* `getJobStatus`: Fetch status and progress of an asynchronous Zendesk background job by ID.
* `getTicketAudits`: Fetch full audit event history for a ticket (field changes, comments, notifications, and trigger/business rule executions).
* `listTicketFields`: List all active ticket fields.
* `getTicketField`: Fetch ticket field by ID (schema discovery).
* `listTicketForms`: List all ticket forms.
* `getTicketForm`: Fetch ticket form by ID.
* `listCustomObjects`: List all custom objects.
* `getCustomObject`: Fetch custom object schema and details by key.
* `listCustomObjectRecords`: List records for a custom object.
* `getCustomObjectRecord`: Fetch a specific custom object record by ID.
* `searchCustomObjectRecords`: Search custom object records matching query text.
* `getToolVersion`: Get the version of the MCP server.

## Resources

* `zendesk-best-practices` (`docs://zendesk/best-practices`): Operational guidance and best practices for Zendesk administration, API usage, rate limit quotas, search sideloading, ticket audit/trigger debugging, and batch operations.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
