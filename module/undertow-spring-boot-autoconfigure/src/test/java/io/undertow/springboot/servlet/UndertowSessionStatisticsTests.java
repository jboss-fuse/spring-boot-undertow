/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.springboot.servlet;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.springboot.autoconfigure.metrics.UndertowMetrics;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.xnio.XnioWorker;

import org.springframework.boot.web.server.WebServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that session statistics are enabled on the deployment's session manager so that
 * the {@code undertow.sessions.*} metrics report real values.
 */
class UndertowSessionStatisticsTests {

    private WebServer webServer;

    @AfterEach
    void stop() {
        if (this.webServer != null) {
            this.webServer.stop();
        }
    }

    @Test
    void sessionStatisticsAreEnabled() throws Exception {
        AtomicReference<SessionManager> sessionManager = new AtomicReference<>();
        AtomicReference<XnioWorker> worker = new AtomicReference<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        this.webServer = factory.getWebServer((servletContext) -> {
            sessionManager.set(((ServletContextImpl) servletContext).getDeployment().getSessionManager());
            servletContext.addServlet("session", new HttpServlet() {
                @Override
                protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                    worker.set(io.undertow.servlet.handlers.ServletRequestContext.requireCurrent().getExchange()
                            .getConnection().getWorker());
                    response.getWriter().print(request.getSession(true).getId());
                }
            }).addMapping("/session");
        });
        this.webServer.start();
        assertThat(sessionManager.get().getStatistics()).isNotNull();
        assertThat(sessionManager.get().getStatistics().getCreatedSessionCount()).isZero();
        HttpClient client = HttpClient.newHttpClient();
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + this.webServer.getPort() + "/session")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
        }
        assertThat(sessionManager.get().getStatistics().getCreatedSessionCount()).isEqualTo(3);
        assertThat(sessionManager.get().getStatistics().getActiveSessionCount()).isEqualTo(3);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new UndertowMetrics(worker.get(), sessionManager.get(), null, java.util.List.of()).bindTo(registry);
        assertThat(registry.get("undertow.sessions.created").functionCounter().count()).isEqualTo(3);
        assertThat(registry.get("undertow.sessions.active.current").gauge().value()).isEqualTo(3);
        assertThat(registry.get("undertow.sessions.active.max").gauge().value()).isNaN();
        assertThat(registry.get("undertow.threads.worker.busy").gauge().value()).isNaN();
    }

}
