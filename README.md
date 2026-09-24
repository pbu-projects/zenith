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

### Tickets & Audits
* `getTicket`: Fetch ticket by ID.
* `getTickets`: Fetch multiple tickets by IDs.
* `listTickets`: List recent tickets.
* `getTicketCount`: Fetch ticket counts.
* `createTicket`: Create ticket (supports subject, comment, status, priority, and attachments).
* `updateTicket`: Update ticket comment, status, priority, or attachments.
* `uploadAttachment`: Upload local file or image and receive an upload token to attach to tickets.
* `batchUpdateTickets`: Batch update multiple tickets concurrently or asynchronously via Zendesk bulk job.
* `getJobStatus`: Fetch status and progress of an asynchronous Zendesk background job by ID.
* `getTicketAudits`: Fetch full audit event history for a ticket (field changes, comments, notifications, and trigger/business rule executions).

### Search
* `search`: Search Zendesk (`type:ticket ...`). Supports sideloading related resources (`include=users,organizations,groups`).
* `searchCount`: Count search results.

### Views
* `listViews`: List all views configured in Zendesk.
* `listActiveViews`: List only active views configured in Zendesk.
* `getView`: Fetch view metadata and details by ID.
* `getViewTickets`: Get tickets from a specific Zendesk view by its numeric ID.
* `executeView`: Execute a specific view to inspect ticket rows and columns.
* `getViewTicketCount`: Get the ticket count for a specific view.

### Ticket Fields & Forms
* `listTicketFields`: List all active ticket fields.
* `getTicketField`: Fetch ticket field by ID (schema discovery).
* `listTicketForms`: List all ticket forms (compact summary by default; optional full payload).
* `getTicketForm`: Fetch ticket form by ID.

### Custom Objects & Records
* `listCustomObjects`: List all custom objects.
* `getCustomObject`: Fetch custom object schema and details by key.
* `listCustomObjectRecords`: List records for a custom object.
* `getCustomObjectRecord`: Fetch a specific custom object record by ID.
* `searchCustomObjectRecords`: Search custom object records matching query text.

### Help Center & Knowledge Base
* `listArticles`: List Help Center / Knowledge Base articles with optional locale, sort, labels, and incremental timestamp.
* `getArticle`: Fetch details and content of a Help Center / Knowledge Base article by ID and optional locale.
* `createArticle`: Create a new article in a Help Center section.
* `updateArticle`: Update an existing Help Center article.
* `deleteArticle`: Delete a Help Center article by its ID (requires confirmation).
* `listTranslations`: List translations for a Help Center resource (articles, sections, categories).
* `getTranslation`: Get a specific translation for a Help Center resource by locale.
* `listCategories`: List Help Center categories across all or specific locales.
* `getCategory`: Fetch Help Center category details by numeric ID.

### Community (Gather)
* `listCommunityTopics`: List all community topics.
* `getCommunityTopic`: Fetch details of a community topic by numeric ID.
* `listCommunityPosts`: List community posts (optionally filtered by topic ID).
* `getCommunityPost`: Fetch details of a community post by numeric ID.
* `searchCommunityPosts`: Search community posts matching query text.
* `listCommunityPostComments`: List comments for a community post.

## Resources

* `serverVersion` (`zenith://version`): Get the version of the MCP server.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
