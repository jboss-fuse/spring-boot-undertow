/*
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
package io.undertow.springboot.autoconfigure.metrics;

import java.util.Collections;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.undertow.Undertow;
import io.undertow.servlet.api.DeploymentManager;

import io.undertow.springboot.UndertowWebServer;
import io.undertow.springboot.servlet.UndertowServletWebServer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.xnio.XnioWorker;

/**
 * Binds {@link UndertowMetrics} to the running Undertow web server after the application
 * has started.
 */
public class UndertowMetricsBinder implements ApplicationListener<ApplicationStartedEvent>, DisposableBean {

    private final MeterRegistry meterRegistry;
    private final Iterable<Tag> tags;
    private volatile UndertowMetrics undertowMetrics;

    public UndertowMetricsBinder(MeterRegistry meterRegistry) {
        this(meterRegistry, Collections.emptyList());
    }

    public UndertowMetricsBinder(MeterRegistry meterRegistry, Iterable<Tag> tags) {
        this.meterRegistry = meterRegistry;
        this.tags = tags;
    }

    @Override
    public void destroy() {
        if (this.undertowMetrics != null) {
            this.undertowMetrics.close();
        }
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        ApplicationContext applicationContext = event.getApplicationContext();
        if (applicationContext instanceof WebServerApplicationContext wsCtx) {
            WebServer webServer = wsCtx.getWebServer();
            if (webServer instanceof UndertowServletWebServer undertowServlet) {
                bindServletMetrics(undertowServlet);
            }
            else if (webServer instanceof UndertowWebServer undertowWeb) {
                bindBasicMetrics(undertowWeb);
            }
        }
    }

    private void bindServletMetrics(UndertowServletWebServer webServer) {
        Undertow undertow = webServer.getUndertow();
        DeploymentManager dm = webServer.getDeploymentManager();
        if (undertow == null) {
            return;
        }
        XnioWorker worker = undertow.getWorker();
        this.undertowMetrics = new UndertowMetrics(
                worker,
                (dm != null) ? dm.getDeployment().getSessionManager() : null,
                (dm != null) ? dm.getDeployment() : null,
                tags);
        this.undertowMetrics.bindTo(this.meterRegistry);
    }

    private void bindBasicMetrics(UndertowWebServer webServer) {
        Undertow undertow = webServer.getUndertow();
        if (undertow == null) {
            return;
        }
        this.undertowMetrics = new UndertowMetrics(undertow.getWorker(), null, null, tags);
        this.undertowMetrics.bindTo(this.meterRegistry);
    }

}
