# Changelog

## 1.0.0

First release under the `io.undertow` group: `io.undertow:undertow-spring-boot-starter`
and `io.undertow:undertow-spring-boot-autoconfigure`. Built against Spring Boot 4.1.2
and Undertow EE 2.0.2.Final (Undertow core 2.4.3.Final).

### Breaking changes (with backward compatibility)

- **Configuration property namespace migration (#3):** All Undertow properties have
  moved from `server.undertow.*` / `management.server.undertow.*` to
  `undertow.server.*` / `undertow.management.*`. The old prefixes are deprecated,
  still work via an automatic mapping shim, and will be removed in the next feature
  release. A `WARN` is logged at startup listing any deprecated keys in use.
  See [MIGRATION.md](MIGRATION.md) for the full old-to-new key table.

### Changes

- **Starter dependencies:** the starter no longer depends on `spring-boot-starter`, so it
  no longer forces Logback on applications. It contains only the auto-configuration,
  Undertow EE (servlet and websockets) and `tomcat-embed-el`.
- **Session metrics:** session statistics are enabled on the servlet deployment's session
  manager, so the `undertow.sessions.*` metrics report real values. Gauges for
  unsupported or unlimited values report `NaN` instead of `-1`.
- **Reactive shutdown:** in-flight reactive requests complete instead of failing with a
  `RejectedExecutionException` when the server is stopped.
- **Stopping and restarting the server:** stopping the web server no longer destroys the
  servlet deployment; that now happens when the server is destroyed, as with Tomcat and
  Jetty. `@PreDestroy` methods can use the `ServletContext` (and `Environment` lookups that
  read its init parameters), `ServletContextListener#contextDestroyed` is called on destroy
  rather than on stop, a stopped server can be started again, and calling `destroy()` more
  than once is tolerated.
- **Configuration metadata:** property metadata is generated for IDE completion.
