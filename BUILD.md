# Building Zenith

## Prerequisites

* GraalVM JDK 25+ with `native-image`
* Live Zendesk test instance (tests execute against live API)

Using SDKMAN:
```bash
sdk install java 25.0.2-graalce
sdk use java 25.0.2-graalce
```

### Test Instance Requirements

* **Credentials**: Placed in `.env` (`ZENDESK_URL`, `ZENDESK_CLIENT_ID`, `ZENDESK_CLIENT_SECRET`).
* **Tickets**: At least 7 tickets; ticket ID `7` must exist.
* **Ticket Fields**: System/custom ticket fields present.

## Build Native Binary

Run tests first. Tests are required to generate the guided profile and metadata for GraalVM:

```bash
# 1. Run tests to generate guided profile
./gradlew test

# 2. Compile native executable
./gradlew nativeCompile
```

Binary output:
`build/native/nativeCompile/zenith`

## Build JVM Binary

```bash
./gradlew installDist
```

Binary output:
`build/install/zenith/bin/zenith`
