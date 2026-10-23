# Undertow Spring Boot Migration Guide

This guide covers the migration from the Spring Boot-maintained starter,
`org.springframework.boot:spring-boot-starter-undertow` (Spring Boot 3.x), to
`io.undertow:undertow-spring-boot-starter`, the Undertow integration for Spring Boot 4.
Spring Boot 4 removed its own Undertow support; this project replaces it using
Undertow EE and Jakarta Servlet 6.1.

The sections are ordered from the most common case to the least common:

1. [From `spring-boot-starter-undertow` to `undertow-spring-boot-starter`](#1-from-spring-boot-starter-undertow-to-undertow-spring-boot-starter):
   for Camel Spring Boot and `camel-platform-http-starter` applications this is normally
   the only change.
2. [Property prefix migration](#2-property-prefix-migration): optional now, required before
   the next feature release.
3. [Java API changes](#3-java-api-changes): only for applications with Undertow customizers
   or factory subclasses.
4. [WebFlux and other dependency layouts](#4-webflux-and-other-dependency-layouts).
5. [Runtime behavior changes](#5-runtime-behavior-changes) that apply to every application.
6. [Automated migration assistance](#6-automated-migration-assistance).
7. [Verifying the migration](#7-verifying-the-migration).

Follow the
[Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
alongside this guide for changes to Spring Framework, Spring Security, testing, and other
Spring Boot features. Applications still on Spring Boot 2.x must complete the Boot 2 to 3
migration first, including the move from `javax.servlet` to `jakarta.servlet`.

## Versions

`1.0.0` is built against Java 17+, Spring Boot 4.1.2, and Undertow EE
2.0.2.Final (Undertow core 2.4.3.Final). These are build baselines, not a
compatibility guarantee for every Spring Boot 4.x release. Check the target release's
[README](README.md), [changelog](CHANGELOG.md), and POM before upgrading.

The starter has its own version line, starting at `1.0.0`. It does not follow the Spring Boot or
Camel version.

## 1. From `spring-boot-starter-undertow` to `undertow-spring-boot-starter`

The typical Camel Spring Boot application uses `camel-platform-http-starter` together
with `spring-boot-starter-web`, excludes Tomcat, and adds the Undertow starter. For that
layout the migration is a coordinate change:

- **Configuration properties keep working.** `server.undertow.*` and
  `management.server.undertow.*` are still read. They are mapped to the new
  `undertow.server.*` and `undertow.management.*` prefixes at startup, and a single
  `WARN` lists them. Renaming is optional for now; see
  [section 2](#2-property-prefix-migration).
- **No Java changes** unless the application imports
  `org.springframework.boot.web.embedded.undertow.*` classes (custom
  `UndertowBuilderCustomizer`, `UndertowDeploymentInfoCustomizer`, or factory
  subclasses). See [section 3](#3-java-api-changes).
- **Camel-owned properties are untouched.** Keys under
  `camel.component.platform-http.server.undertow.*` belong to Camel Spring Boot, are not
  remapped, and do not trigger the deprecation warning.

Before:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-undertow</artifactId>
</dependency>
```

After:

```xml
<dependency>
    <groupId>io.undertow</groupId>
    <artifactId>undertow-spring-boot-starter</artifactId>
</dependency>
```

The Red Hat Camel Spring Boot BOM (`camel-spring-boot-bom`) for Camel 4.22.1 and later
manages the version of `io.undertow:undertow-spring-boot-starter`, so no `<version>` is
needed when that BOM is imported. Without it, declare the version explicitly; the Spring
Boot BOM does not manage this starter or any Undertow artifact.

A complete `camel-platform-http-starter` dependency block after the change:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>io.undertow</groupId>
    <artifactId>undertow-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-platform-http-starter</artifactId>
</dependency>
```

The Tomcat exclusion is the same one the Spring Boot 3 starter required. Apply it to
Boot 4's `spring-boot-starter-webmvc` as well if you move to that starter. Remove other
embedded server starters such as Jetty; the Undertow factory auto-configuration backs off
when another `ServletWebServerFactory` bean is present.

### Undertow libraries and logging

The Servlet and WebSocket implementations are now `io.undertow.ee:undertow-servlet` and
`io.undertow.ee:undertow-websockets`. Undertow core remains `io.undertow:undertow-core`,
and all Java packages of the Undertow libraries remain `io.undertow.*`. Review old
direct dependencies, exclusions, and version overrides on `io.undertow:undertow-servlet`
or `io.undertow:undertow-websockets-jsr`; they no longer apply.

The starter contains the auto-configuration, Undertow EE, and `tomcat-embed-el`
(expression-language support only, no Tomcat server). Unlike the Spring Boot 3 starter it
does not depend on `spring-boot-starter`. Camel applications are unaffected, because
`camel-platform-http-starter`, `spring-boot-starter-web`, and `camel-spring-boot-starter`
all bring `spring-boot-starter` and its logging. A minimal application that relied on the
Undertow starter for logging must add `spring-boot-starter` or a logging implementation.

After the dependency change, run the checks in [section 7](#7-verifying-the-migration).

## 2. Property prefix migration

Undertow-specific properties have moved out of the `server.*` and `management.*`
namespaces reserved by Spring Boot. The old prefixes are deprecated, still work, and
will be removed in the next feature release.

Only Undertow-specific properties move. Keep general Spring Boot properties such as
`server.port`, `server.ssl.*`, `server.servlet.*`, `server.compression.*`,
`server.forward-headers-strategy`, and `management.server.port` where they are.

### Behavior during the deprecation period

- Old keys are mapped to the new keys at startup by an `EnvironmentPostProcessor`.
- If both the old and the new key are set for the same property, the **new key wins**,
  even when the old key comes from a normally higher-priority source such as a
  command-line argument. Avoid mixing prefixes across profiles, environment variables,
  and external configuration.
- One consolidated `WARN` is logged once the application is ready, listing every
  deprecated key in use and its replacement, and every deprecated key that was ignored
  because the new key was also set.
- Environment variables are mapped too: `SERVER_UNDERTOW_THREADS_IO` becomes
  `UNDERTOW_SERVER_THREADS_IO`, `MANAGEMENT_SERVER_UNDERTOW_ACCESSLOG_PREFIX` becomes
  `UNDERTOW_MANAGEMENT_ACCESSLOG_PREFIX`.
- The mapper reads enumerable property sources during environment preparation. It does
  not translate properties added later or exposed only through a non-enumerable custom
  property source; use the new keys there.

### Example

Before:

```yaml
server:
  undertow:
    threads:
      io: 4
      worker: 32
    accesslog:
      enabled: true
```

After:

```yaml
undertow:
  server:
    threads:
      io: 4
      worker: 32
    accesslog:
      enabled: true
```

Rename deployment configuration (environment variables, ConfigMaps, launch scripts)
together with the application files.

### Old → New Key Table

#### Server properties (`server.undertow.*` → `undertow.server.*`)

| Deprecated key | New key |
|---|---|
| `server.undertow.max-http-post-size` | `undertow.server.max-http-post-size` |
| `server.undertow.buffer-size` | `undertow.server.buffer-size` |
| `server.undertow.direct-buffers` | `undertow.server.direct-buffers` |
| `server.undertow.eager-filter-init` | `undertow.server.eager-filter-init` |
| `server.undertow.max-parameters` | `undertow.server.max-parameters` |
| `server.undertow.max-headers` | `undertow.server.max-headers` |
| `server.undertow.max-cookies` | `undertow.server.max-cookies` |
| `server.undertow.decode-slash` | `undertow.server.decode-slash` |
| `server.undertow.decode-url` | `undertow.server.decode-url` |
| `server.undertow.url-charset` | `undertow.server.url-charset` |
| `server.undertow.always-set-keep-alive` | `undertow.server.always-set-keep-alive` |
| `server.undertow.no-request-timeout` | `undertow.server.no-request-timeout` |
| `server.undertow.preserve-path-on-forward` | `undertow.server.preserve-path-on-forward` |
| `server.undertow.accesslog.enabled` | `undertow.server.accesslog.enabled` |
| `server.undertow.accesslog.pattern` | `undertow.server.accesslog.pattern` |
| `server.undertow.accesslog.prefix` | `undertow.server.accesslog.prefix` |
| `server.undertow.accesslog.suffix` | `undertow.server.accesslog.suffix` |
| `server.undertow.accesslog.dir` | `undertow.server.accesslog.dir` |
| `server.undertow.accesslog.rotate` | `undertow.server.accesslog.rotate` |
| `server.undertow.threads.io` | `undertow.server.threads.io` |
| `server.undertow.threads.worker` | `undertow.server.threads.worker` |
| `server.undertow.options.server.*` | `undertow.server.options.server.*` |
| `server.undertow.options.socket.*` | `undertow.server.options.socket.*` |

#### Management properties (`management.server.undertow.*` → `undertow.management.*`)

| Deprecated key | New key |
|---|---|
| `management.server.undertow.accesslog.prefix` | `undertow.management.accesslog.prefix` |

### Removed properties

`server.undertow.allow-encoded-slash`, deprecated in Spring Boot 3, has no
counterpart here and renaming its prefix does not restore it. If the application needs
encoded slashes decoded, evaluate `undertow.server.decode-slash` explicitly and test the
resulting paths through the front-end proxy and the application.

`server.undertow.buffers-per-region` has no replacement and is deliberately excluded
from the compatibility mapping. Remove it.

## 3. Java API changes

Applications using only controllers, Camel routes, and configuration properties need no
Undertow-specific Java changes. Applications with customizers, factory subclasses, or
explicit auto-configuration imports/excludes must update them:

| Spring Boot 3 API | Replacement |
|---|---|
| `org.springframework.boot.web.embedded.undertow.UndertowBuilderCustomizer` | `io.undertow.springboot.UndertowBuilderCustomizer` |
| `org.springframework.boot.web.embedded.undertow.ConfigurableUndertowWebServerFactory` | `io.undertow.springboot.ConfigurableUndertowWebServerFactory` |
| `org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory` | `io.undertow.springboot.servlet.UndertowServletWebServerFactory` |
| `org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer` | `io.undertow.springboot.servlet.UndertowDeploymentInfoCustomizer` |
| `org.springframework.boot.web.embedded.undertow.UndertowReactiveWebServerFactory` | `io.undertow.springboot.reactive.UndertowReactiveWebServerFactory` |
| `org.springframework.boot.autoconfigure.web.ServerProperties.Undertow` / `ServerProperties.getUndertow()` | Inject `io.undertow.springboot.autoconfigure.UndertowServerProperties`. |

The Maven group ID is `io.undertow`, while the integration's Java package is
`io.undertow.springboot`. For example, a builder customizer now uses:

```java
import io.undertow.UndertowOptions;
import io.undertow.springboot.UndertowBuilderCustomizer;
import org.springframework.context.annotation.Bean;

// Inside an application configuration class
@Bean
UndertowBuilderCustomizer undertowBuilderCustomizer() {
    return builder -> builder.setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, false);
}
```

This is a source migration, not a binary-compatible replacement for libraries compiled
against Spring Boot's old Undertow classes. Recompile customizations and review factory
API usage against Spring Boot 4's web server abstractions.

## 4. WebFlux and other dependency layouts

### WebFlux

Keep `spring-boot-starter-webflux` and exclude Reactor Netty:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-reactor-netty</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

Review other dependency paths that may bring a competing server back onto the
classpath. Do not add the MVC starter just to obtain Servlet classes: the Undertow starter
supplies its Servlet runtime, and adding MVC changes Spring Boot's application-type
detection.

### Auto-configuration module only

Applications that deliberately manage the Undertow runtime dependencies themselves can
depend on `io.undertow:undertow-spring-boot-autoconfigure` instead of the starter. Prefer
the starter otherwise.

### External containers

For a WAR deployed to an external application server, the container supplies the
server. This starter does not upgrade the container or replace its Undertow
implementation; its web server configuration is skipped for WAR deployments. Keep
embedded server dependencies in `provided` scope and test executable WAR and external
deployment modes separately if both are used.

## 5. Runtime behavior changes

### Reactive HTTP and WebSockets

Spring Boot 3's reactive integration used `UndertowHttpHandlerAdapter`. This integration
uses `ServletHttpHandlerAdapter` on an Undertow Servlet deployment, the same bridge
Spring Boot uses for Jetty. WebFlux applications keep working, but should verify
streaming responses, server-sent events, cancellation, error handling, and requests in
flight during shutdown.

Servlet WebSocket support is configured by the servlet auto-configuration. Do not infer
reactive WebSocket compatibility from servlet WebSocket support; verify the application's
reactive upgrade strategy with a complete handshake and message exchange.

### Persisted HTTP sessions

With `server.servlet.session.persistent=true`, container session files contain
Java-serialized instances of the integration's internal
`FileSessionPersistence.SerializablePersistentSession` class. Its package differs from
the Spring Boot implementation, and there is no translation of old serialized class names.

Do not expect existing `.session` files to survive the migration. Plan for session
invalidation and user re-authentication, or use a separate session-store directory for
the new deployment if old files must be retained for rollback. Spring Session with Redis
or JDBC requires its own compatibility review, including the serialization of
application session attributes.

### Request limits

The default maximum HTTP POST size remains 2 MiB, as in Spring Boot 3.5. An explicit
value migrates to `undertow.server.max-http-post-size`. Negative values map to
Undertow's unlimited setting; retain an explicit bound unless unlimited requests are
required.

### Server lifecycle

The servlet deployment is destroyed when the web server is destroyed, not when it is
stopped, matching Tomcat and Jetty. A stopped server can be started again, and
`ServletContextListener.contextDestroyed` runs on destroy. Applications that explicitly
stop and restart the server or depend on that timing should review it, and verify
graceful shutdown with active requests. See the [changelog](CHANGELOG.md).

### Actuator and metrics

If Actuator uses a separate management port, its listener, TLS, access-log prefix
(`undertow.management.accesslog.prefix`), and metrics are configured independently.
Session statistics are enabled on the deployment, so `undertow.sessions.*` metrics report
real values; unsupported or unlimited gauge values are `NaN` rather than `-1`.

### Camel `camel-undertow` component

Camel's `camel-undertow` component and `camel-undertow-starter` configure Camel's own
Undertow endpoints. They are unrelated to the embedded Spring Boot web server configured
by this starter, and an application can use both. `camel-platform-http-starter`, by
contrast, runs on the embedded web server and is the usual reason this starter is present.

## 6. Automated migration assistance

The downstream Camel Spring Boot upgrade recipes
([Camel Upgrade Recipes](https://camel.apache.org/manual/camel-upgrade-recipes-tool.html),
`camel-spring-boot-upgrade-recipes`) rewrite the
`org.springframework.boot:spring-boot-starter-undertow` dependency as part of the Camel
4.19 / Spring Boot 4 upgrade. After running them, check that the resulting coordinate is
`io.undertow:undertow-spring-boot-starter`; recipe sets published before this starter may
write an obsolete coordinate, which must be replaced as shown in
[section 1](#1-from-spring-boot-starter-undertow-to-undertow-spring-boot-starter).

The recipes do not migrate Undertow property prefixes, Java imports, or persisted
sessions. Complete those steps manually where they apply.

## 7. Verifying the migration

1. Inspect the effective dependency graph with `mvn dependency:tree` (or the Gradle
   equivalent) and resolve the runtime artifacts with `mvn dependency:resolve`. Confirm
   that `io.undertow:undertow-spring-boot-starter`, `io.undertow.ee:undertow-servlet`,
   and `io.undertow.ee:undertow-websockets` resolve, that
   `org.springframework.boot:spring-boot-starter-undertow` is absent, and that no Tomcat,
   Jetty, or Reactor Netty server starter remains. POM validation alone does not resolve
   dependencies.
2. Start the application and confirm Undertow serves its endpoints. Read the startup
   log for the deprecated-property `WARN` and plan the renames it lists.
3. Verify settings in every deployment profile, including environment-variable
   overrides, TLS, forwarded headers, context paths, access logs, and request limits.
4. Exercise authentication, session cookies, uploads, encoded paths, and WebSockets used
   by the application. For WebFlux, include streaming and cancellation tests.
5. If Actuator uses a separate management port, verify it independently.
6. Verify shutdown, restart, and the planned session/rollback behavior. If producing a
   native image, build and exercise the native application as well.
