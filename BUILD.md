# Building Zenith

## Prerequisites

* GraalVM JDK 25+ with `native-image`

Using SDKMAN:
```bash
sdk install java 25.0.2-graalce
sdk use java 25.0.2-graalce
```

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
