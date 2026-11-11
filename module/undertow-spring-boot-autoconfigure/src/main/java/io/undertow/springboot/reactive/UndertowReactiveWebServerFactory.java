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
package io.undertow.springboot.reactive;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainerInitializerInfo;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.xnio.XnioWorker;

import io.undertow.springboot.ConfigurableUndertowWebServerFactory;
import io.undertow.springboot.HttpHandlerFactory;
import io.undertow.springboot.UndertowWebServer;
import io.undertow.springboot.UndertowWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.reactive.ConfigurableReactiveWebServerFactory;
import org.springframework.boot.web.server.reactive.ReactiveWebServerFactory;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;

/**
 * {@link ReactiveWebServerFactory} that can be used to create {@link UndertowWebServer}s.
 * <p>
 * Uses the Servlet bridge pattern ({@link ServletHttpHandlerAdapter}) to handle reactive
 * HTTP requests through Undertow's servlet container, following the same approach as
 * Spring Boot's Jetty reactive support.
 *
 * @since 4.0.0
 */
public class UndertowReactiveWebServerFactory extends UndertowWebServerFactory
        implements ConfigurableUndertowWebServerFactory, ConfigurableReactiveWebServerFactory {

    public UndertowReactiveWebServerFactory() {
    }

    public UndertowReactiveWebServerFactory(int port) {
        super(port);
    }

    @Override
    public WebServer getWebServer(org.springframework.http.server.reactive.HttpHandler httpHandler) {
        ServletHttpHandlerAdapter servlet = new ServletHttpHandlerAdapter(httpHandler);
        Undertow.Builder builder = createBuilder(this, this::getSslBundle, this::getServerNameSslBundles);
        List<HttpHandlerFactory> httpHandlerFactories = createHttpHandlerFactories(this,
                new ReactiveHttpHandlerFactory(servlet));
        return new UndertowWebServer(builder, httpHandlerFactories, getPort() >= 0);
    }

    /**
     * {@link HttpHandlerFactory} that creates a fresh Undertow servlet deployment for
     * each call, wrapping the reactive {@link ServletHttpHandlerAdapter}. This supports
     * stop/start cycles because a new deployment is created on each start.
     */
    private static class ReactiveHttpHandlerFactory implements HttpHandlerFactory {

        private final ServletHttpHandlerAdapter servlet;

        ReactiveHttpHandlerFactory(ServletHttpHandlerAdapter servlet) {
            this.servlet = servlet;
        }

        @Override
        public @Nullable HttpHandler getHandler(@Nullable HttpHandler next) {
            DeploymentInfo deployment = Servlets.deployment();
            deployment.setClassLoader(getClass().getClassLoader());
            deployment.setContextPath("/");
            deployment.setDeploymentName("spring-boot-reactive");
            WorkerFallbackExecutor asyncExecutor = new WorkerFallbackExecutor();
            deployment.setAsyncExecutor(asyncExecutor);
            deployment.addServletContainerInitializer(new ServletContainerInitializerInfo(
                    ReactiveServletInitializer.class,
                    new ImmediateInstanceFactory<>(new ReactiveServletInitializer(this.servlet)),
                    Set.of()));
            DeploymentManager manager = Servlets.newContainer().addDeployment(deployment);
            manager.deploy();
            try {
                return new DeploymentHandler(manager, manager.start(), asyncExecutor);
            }
            catch (ServletException ex) {
                throw new RuntimeException(ex);
            }
        }

    }

    private static class DeploymentHandler implements HttpHandler, Closeable {

        private final DeploymentManager manager;
        private final HttpHandler handler;

        private final WorkerFallbackExecutor asyncExecutor;

        DeploymentHandler(DeploymentManager manager, HttpHandler handler, WorkerFallbackExecutor asyncExecutor) {
            this.manager = manager;
            this.handler = handler;
            this.asyncExecutor = asyncExecutor;
        }

        @Override
        public void handleRequest(io.undertow.server.HttpServerExchange exchange) throws Exception {
            this.asyncExecutor.setWorker(exchange.getConnection().getWorker());
            this.handler.handleRequest(exchange);
        }

        @Override
        public void close() throws IOException {
            if (this.manager.getState() != DeploymentManager.State.UNDEPLOYED) {
                try {
                    this.manager.stop();
                    this.manager.undeploy();
                }
                catch (ServletException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

    }

    /**
     * Async {@link Executor} that delegates to the XNIO worker while it is running and
     * falls back to executing on the calling thread once the worker has been shut down.
     * {@code Undertow.stop()} shuts the worker down while requests may still be in
     * flight; without the fallback, completing such a request (which the servlet bridge
     * does via {@code AsyncContext.complete()}) fails with a
     * {@link RejectedExecutionException} instead of finishing.
     */
    static final class WorkerFallbackExecutor implements Executor {

        private static final Log logger = LogFactory.getLog(WorkerFallbackExecutor.class);

        private volatile @Nullable XnioWorker worker;

        void setWorker(XnioWorker worker) {
            // Undertow creates a new worker each time the server is started, so replace a
            // worker that has been shut down
            XnioWorker current = this.worker;
            if (current != worker && (current == null || current.isShutdown())) {
                this.worker = worker;
            }
        }

        @Override
        public void execute(Runnable task) {
            XnioWorker worker = this.worker;
            if (worker != null && !worker.isShutdown()) {
                try {
                    worker.execute(task);
                    return;
                }
                catch (RejectedExecutionException ex) {
                    // Worker shut down concurrently; run on the calling thread instead
                }
            }
            try {
                task.run();
            }
            catch (RejectedExecutionException ex) {
                // The server is shutting down and its IO threads are gone; the response for
                // this in-flight request can no longer be written, which is expected.
                logger.debug("Dropped async task for in-flight request during shutdown", ex);
            }
        }

    }

    private static class ReactiveServletInitializer implements ServletContainerInitializer {

        private final ServletHttpHandlerAdapter servlet;

        ReactiveServletInitializer(ServletHttpHandlerAdapter servlet) {
            this.servlet = servlet;
        }

        @Override
        public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {
            ServletRegistration.Dynamic registration = servletContext.addServlet("http-handler-adapter", this.servlet);
            registration.setLoadOnStartup(1);
            registration.addMapping("/");
            registration.setAsyncSupported(true);
        }

    }

}
