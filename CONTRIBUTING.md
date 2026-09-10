# Contributing to Zenith

By participating in this project, you agree to abide by our
[Code of Conduct](CODE_OF_CONDUCT.md).

Thank you for contributing to Zenith! Below are guidelines, instructions, and standards for contributing to this project.

---

## Style Guide

- This project follows [Google's Java Style Guide](https://google.github.io/styleguide/javaguide.html).
- We follow (and enforce) [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`, `ci:`).

---

## Machine Setup

`zenith` is written in Java 25, compiled natively with [GraalVM CE](https://www.graalvm.org/), and built using [Gradle](https://gradle.org/).

### Prerequisites
* **Java**: [GraalVM Community Edition 25](https://www.graalvm.org/) (`java -version` should show GraalVM CE 25+).
* **Gradle**: Handled via the provided `./gradlew` wrapper (no local Gradle installation required).
* **Native Build Tools**:
  * **Linux**: `gcc`, `glibc-devel`, `zlib-devel`
  * **macOS**: Xcode Command Line Tools (`xcode-select --install`)
  * **Windows**: Visual Studio C++ Build Tools (MSVC)
* **Git**: Modern git client.

### Getting Started

Clone the repository and build the native image:

```shell
git clone git@github.com:pbu-projects/zenith.git
cd zenith
./gradlew test nativeCompile
```

The resulting native binary will be generated at:
* Linux / macOS: `build/native/nativeCompile/zenith`
* Windows: `build/native/nativeCompile/zenith.exe`

---

## Architecture & Design Principles

### Strict STDIO Protocol Integrity
> [!IMPORTANT]
> **Zero stdout Pollution**
> Zenith communicates with MCP client hosts (Claude Desktop, Cursor, AI agents) exclusively over standard input/output (`stdin`/`stdout`) using the JSON-RPC 2.0 protocol.
>
> * **Never write to `System.out`**: Any non-JSON-RPC output on `stdout` breaks host deserialization and terminates the MCP session.
> * **Logging**: All application and diagnostic logging MUST use SLF4J/Logback routed strictly to `System.err`.
> * `logback.xml` must keep `<statusListener class="ch.qos.logback.core.status.NopStatusListener" />` active to prevent JVM startup warnings on stdout.

### GraalVM Native Image Compatibility
All production code must compile ahead-of-time (AOT) to native binaries via `./gradlew nativeCompile`:
* **Serialization**: Annotate all tool argument records, data transfer objects, and response models with `@io.micronaut.serde.annotation.Serdeable`.
* **Reflection**: Avoid dynamic, unconfigured reflection or runtime classloading.
* **Lightweight Startup**: Keep startup execution paths fast. Authentication token acquisition is lazy and cached via `ZendeskTokenProvider`.

### Separation of Concerns: Zenith vs z4j
* **Zenith**: Focuses strictly on Model Context Protocol (MCP) tool exposure, argument validation, and AI agent usability.
* **z4j**: When Zendesk REST API client logic, endpoints, or response models are missing, add them to [`lol.pbu:z4j`](https://github.com/pbu-projects/z4j) rather than embedding raw HTTP client logic directly in Zenith.

---

## Testing

### End-to-End MCP Protocol Testing
Zenith tests are written in Spock 2 (`src/test/groovy/`) and exercise the server over actual STDIO transport using the official Model Context Protocol Java SDK (`McpClient` + `StdioClientTransport`).

Gradle automatically builds the staged distribution before testing:
```kotlin
tasks.named("test") {
    dependsOn("installDist")
}
```

Run tests locally:
```shell
./gradlew test
```

### Zendesk Sandbox Configuration

To test tool execution against the live Zendesk API, configure credentials via a `.env` file in the project root (ignored by git):

```properties
ZENDESK_URL=https://<subdomain>.zendesk.com
ZENDESK_CLIENT_ID=<client_id>
ZENDESK_CLIENT_SECRET=<client_secret>
ZENDESK_OAUTH_SCOPE=read write
```

Alternatively, supply a static token:
```properties
ZENDESK_URL=https://<subdomain>.zendesk.com
ZENDESK_OAUTH_TOKEN=<token>
```

### Mandatory Live Environment Policy
> [!IMPORTANT]
> **No Mocking & Real Environment Mandatory**
> Zenith tests execute against an actual Zendesk instance. Both local test execution and CI workflows strictly require valid Zendesk credentials (`ZENDESK_URL`, `ZENDESK_CLIENT_ID`, `ZENDESK_CLIENT_SECRET`). Tests fail immediately if credentials are missing or invalid.

---

## Testing Commandments

1. **Verify over STDIO**: Tool additions and modifications must be validated through `ZendeskMcpClientSpec` over the live MCP protocol.
2. **Native Compilation Verification**: Verify native compilation succeeds (`./gradlew nativeCompile`) before opening pull requests to ensure GraalVM reflection configuration and `@Serdeable` annotations are intact.
3. **Focused Tests**: Each test feature should test a specific MCP method or tool scenario.
4. **Clean Commits**: Ensure commit messages adhere strictly to Conventional Commits.
