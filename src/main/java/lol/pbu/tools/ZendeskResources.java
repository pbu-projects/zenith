package lol.pbu.tools;

import io.micronaut.mcp.annotations.Resource;
import jakarta.inject.Singleton;

@Singleton
public class ZendeskResources {

    @Resource(
        name = "zendesk-best-practices",
        uri = "docs://zendesk/best-practices",
        description = "Operational guidance and best practices for Zendesk administration, API usage, rate limit quotas, search sideloading, ticket audit/trigger debugging, and batch operations"
    )
    public String getBestPractices() {
        return """
## Rate Limits & Quota Management
- Zendesk enforces both global per-minute quotas and endpoint-specific rate limits.
- Global headers: `ratelimit-remaining`, `ratelimit-limit`, `ratelimit-reset`.
- Endpoint-specific headers: `zendesk-ratelimit-<endpoint>` (e.g. `zendesk-ratelimit-search-index` with 2,500/min quota; `zendesk-ratelimit-tickets-index` with 100,000/min quota).
- Search is limited separately and more strictly (2,500/min) than ticket operations.
- On HTTP 429 errors, inspect the `Retry-After` header and back off with exponential jitter before retrying.

## Search & Sideloading
- Zendesk Search requires scoped syntax for sideloading, e.g. `include=tickets(users,organizations,groups)`.
- The `search` MCP tool automatically wraps simple comma-separated lists (e.g. `users,organizations`) in `tickets(...)`.
- Always sideload related users or organizations when performing batch searches to avoid N+1 API calls.

## Ticket Audits & Trigger/Automation Debugging
- Call `getTicketAudits(ticketId)` to view the chronological audit history and identify why a ticket changed.
- In each audit event:
  - `via.channel == "rule"` indicates a business rule executed.
  - `via.source.rel == "trigger"` indicates a trigger fired.
  - `via.source.from.title` provides the name/title of the trigger that executed.
  - `via.source.from.id` provides the unique trigger ID.
  - `field_name`, `value`, and `previous_value` reveal the precise changes applied by the trigger.
  - Notification events reveal email recipients and subject lines generated.

## Batch Ticket Updates
- Use `batchUpdateTickets` for updating up to 100 tickets simultaneously rather than invoking `updateTicket` in a loop.
- The tool returns a `BatchUpdateResponse` detailing the status of each ticket ID.
- If an asynchronous job ID is returned, use `getJobStatus(jobId)` to monitor completion.

## Attachments & File Uploads
- Attachments can be uploaded directly via `uploadAttachment` using a local file path.
- Alternatively, pass `attachmentFilePaths` directly to `createTicket`, `updateTicket`, or `batchUpdateTickets` to automatically upload and link files in a single call.
""";
    }
}
