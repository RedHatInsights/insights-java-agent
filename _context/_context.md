# insights-java-agent — Project Context

## Identity

| Field | Value |
|---|---|
| **Maven coordinates** | `com.redhat.insights:runtimes-agent:1.0.4` |
| **Artifact name** | Red Hat Insights Java Agent |
| **License** | Apache 2.0 |
| **Java target** | Java 8 (source and target) |
| **Packaging** | `jar` (+ shaded uber-jar + jar-with-dependencies) |
| **Repository** | `adopt_bob` branch |

## Purpose

A `-javaagent` that attaches to a running JVM (statically via `premain`, or dynamically via `agentmain`/`Attach`), collects classpath JAR metadata and JVM system properties, then uploads a gzip-compressed JSON report to **Red Hat Insights**. Useful for Red Hat subscribers who want visibility into their Java workloads.

Two delivery modes are supported:
- **OCP (OpenShift)**: bearer-token HTTP upload to `https://cert.console.redhat.com/api/ingress/v1/upload`
- **RHEL/non-OCP**: write report to local file (via `InsightsFileWritingClient`)

## Architecture

```
AgentMain (premain / agentmain)
  ├── AgentConfiguration       — arg parsing + env/sysprop config
  ├── ClassNoticer             — ClassFileTransformer; passively discovers loaded JARs
  ├── AgentBasicReport         — top-level report (extends AbstractTopLevelReportBase)
  │     ├── ClasspathJarInfoSubreport  — from runtimes-java-api
  │     └── AgentSubreport     — workload fingerprint + OCP/pod metadata
  └── InsightsAgentHttpClient  — Apache HttpClient multipart POST (token or mTLS)
        or InsightsFileWritingClient (non-OCP)

Attach                         — standalone main; dynamic attach via VirtualMachine API
AgentLogger                    — singleton SLF4J adapter
shaded/ShadeLogger             — jul shim (relocated by Shade plugin)
```

## Package Structure

```
src/main/java/com/redhat/insights/agent/
├── AgentMain.java              Entry point — premain, agentmain, startAgent
├── AgentConfiguration.java     Config: agent args, env vars, token resolution, isOCP()
├── InsightsAgentHttpClient.java HTTP upload: bearer-token + mTLS, proxy, retry, gzip
├── AgentBasicReport.java       Top-level report: pid, packages, subreports
├── AgentSubreport.java         Workload fingerprint (Spring Boot, Quarkus, Tomcat, JBoss)
├── AgentSubreportSerializer.java Jackson serializer for AgentSubreport
├── ClassNoticer.java           ClassFileTransformer — discovers JARs from code sources
├── Attach.java                 CLI dynamic-attach tool
├── AgentLogger.java            SLF4J-backed InsightsLogger singleton
└── shaded/
    └── ShadeLogger.java        JUL shim relocated by maven-shade-plugin
```

## Key Conventions

- **Agent argument format**: semicolon-separated `key=value` pairs, e.g.  
  `-javaagent:runtimes-java-agent-1.0.4.jar=name=my_app;token=<bearer>`
- **Mandatory arg**: `name` (application identifier)
- **Token resolution order**: inline `token` arg → `token_file` arg → inherited env/sysprop
- **`isOCP()`**: returns `true` if a bearer token is present; determines HTTP vs file mode
- **Subreport version**: `"1.0.1"` — bump when `AgentSubreport` serialization changes
- **Duplicate JAR detection**: by URL + SHA-512

### Agent Arguments Reference

| Arg | Default | Description |
|---|---|---|
| `name` | *(required)* | Application name |
| `opt_out` | `false` | Disable reporting entirely |
| `token` | empty | Bearer token (OCP) |
| `token_file` | empty | Path to file containing bearer token |
| `base_url` | `https://cert.console.redhat.com` | Insights base URL |
| `uri` | `/api/ingress/v1/upload` | Upload path |
| `proxy` | empty | Proxy host |
| `proxy_port` | empty | Proxy port |
| `debug` | `false` | Enable debug logging |
| `defer` | — | Defer reporting start |
| `pod_name` | — | OCP pod name |
| `pod_namespace` | — | OCP pod namespace |

## Dependencies

### Runtime / compile

| Artifact | Version |
|---|---|
| `com.redhat.insights:runtimes-java-api` | `2.0.4` |
| `org.apache.httpcomponents:httpclient` | `4.5.14` |
| `org.apache.httpcomponents:httpcore` | `4.4.16` |
| `org.apache.httpcomponents:httpmime` | `4.5.14` |
| `org.slf4j:slf4j-api` | `2.0.7` |
| `org.slf4j:slf4j-simple` | `2.0.7` |
| `org.slf4j:jcl-over-slf4j` | `2.0.7` |
| `jdk.tools:jdk.tools` | `jdk1.8.0` *(system scope)* |

### Test

| Artifact | Version |
|---|---|
| `org.junit.jupiter:junit-jupiter` | `5.9.2` |
| `org.mockito:mockito-core` | `4.11.0` |
| `org.mockito:mockito-junit-jupiter` | `4.11.0` |
| `com.github.tomakehurst:wiremock-jre8` | `2.35.2` |
| `org.awaitility:awaitility` | `4.2.0` |
| `net.bytebuddy:byte-buddy` | `1.14.0` |
| `net.bytebuddy:byte-buddy-agent` | `1.14.0` |

## Build

```bash
# Full build + unit tests
mvn clean install

# Unit tests only
mvn clean test

# With coverage (output: target/site/jacoco/index.html)
mvn clean test -Pcoverage

# Format code
mvn spotless:apply

# Integration tests (opt-in)
mvn clean test -Pintegration
```

Spotless runs `spotless:check` on every `compile`. Use `mvn spotless:apply` before committing.  
Copyright headers must match `/* Copyright (C) Red Hat $YEAR */`.

## Build Artifacts

| Classifier | Description |
|---|---|
| *(none)* | Standard jar |
| `shaded` | Uber-jar with relocated deps (for use as `-javaagent`) |
| `jar-with-dependencies` | All deps bundled (assembly plugin) |

Shaded jar manifest entries:
- `Premain-Class: com.redhat.insights.agent.AgentMain`
- `Agent-Class: com.redhat.insights.agent.AgentMain`
- `Multi-Release: true`

Shade relocations include: Apache HttpClient, SLF4J, Jackson, JBoss, WildFly packages.

## Release Process

1. GitHub Actions workflow (`.github/workflows/release.yaml`) — manually triggered with `version` and `nextVersion` inputs
2. Runs on `ubuntu-latest` with Java 8 (Temurin)
3. Sets release version → builds → runs `./release.sh` → commits → bumps to next `-SNAPSHOT`
4. `release.sh` stages with `-Ppublication` then calls `jreleaser:full-release` with `-Prelease`
5. Signs and deploys to Maven Central / Sonatype Central Snapshots

## Testing

- **Unit tests**: `ClassNoticerTest`, `InsightsAgentHttpClientTest`, `AgentBasicReportTest`
- **Test doubles**: `NoopInsightsLogger`, `MockInsightsConfiguration`
- **Frameworks**: JUnit 5, Mockito, WireMock (HTTP), Byte Buddy (instrumentation), Awaitility (async)
- **Integration tests**: tagged `@Tag("IntegrationTest")`, excluded by default, run with `-Pintegration`

## Database

`V1.0.X__jvm_psdata.sql` — standalone PostgreSQL DDL (not wired to a migration framework in this module).  
Creates `public.jvm_psdata` table with fields: `id`, `account_id`, `org_id`, `hostname`, `launch_time`, `heap_min/max`, `java_class_path`, `jvm_args`, `major_version`, `vendor`, OS/version fields, `processors`, `created`.

## Code Style

- **Formatter**: Google Java Format (GOOGLE style, via Spotless)
- **Constructor preference** over static factories (unless initialization logic requires a factory)
- **JavaBeans** getters/setters for Jackson-serialized types
- **`Insights` prefix** on top-level interface definitions (e.g., `InsightsHttpClient`)
- See [`STYLE.md`](../STYLE.md) for full guide

## CI/CD

Single workflow: `.github/workflows/release.yaml` — manual release only. No separate PR/push CI workflow is present.

## Notable Files

| File | Purpose |
|---|---|
| [`pom.xml`](../pom.xml) | Maven build descriptor |
| [`src/main/java/.../AgentMain.java`](../src/main/java/com/redhat/insights/agent/AgentMain.java) | Agent entry points |
| [`src/main/java/.../AgentConfiguration.java`](../src/main/java/com/redhat/insights/agent/AgentConfiguration.java) | All configuration |
| [`src/main/java/.../InsightsAgentHttpClient.java`](../src/main/java/com/redhat/insights/agent/InsightsAgentHttpClient.java) | HTTP upload client |
| [`src/main/java/.../AgentSubreport.java`](../src/main/java/com/redhat/insights/agent/AgentSubreport.java) | Workload fingerprinting |
| [`src/main/java/.../ClassNoticer.java`](../src/main/java/com/redhat/insights/agent/ClassNoticer.java) | JAR discovery transformer |
| [`V1.0.X__jvm_psdata.sql`](../V1.0.X__jvm_psdata.sql) | DB schema migration |
| [`release.sh`](../release.sh) | Release staging + JReleaser |
| [`.github/workflows/release.yaml`](../.github/workflows/release.yaml) | GitHub Actions release |
| [`STYLE.md`](../STYLE.md) | Code style guide |
