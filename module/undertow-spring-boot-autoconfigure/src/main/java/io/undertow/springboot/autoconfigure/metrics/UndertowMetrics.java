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

import java.util.function.Supplier;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.api.Deployment;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.xnio.XnioWorker;

/**
 * {@link MeterBinder} for Undertow server internals: XNIO worker threads, sessions,
 * and JMX thread pool metrics.
 */
public class UndertowMetrics implements MeterBinder, AutoCloseable {

    private static final Log LOG = LogFactory.getLog(UndertowMetrics.class);

    private final XnioWorker xnioWorker;
    private final @Nullable SessionManager sessionManager;
    private final @Nullable Deployment deployment;
    private final Iterable<Tag> tags;

    public UndertowMetrics(XnioWorker xnioWorker, @Nullable SessionManager sessionManager,
            @Nullable Deployment deployment, Iterable<Tag> tags) {
        this.xnioWorker = xnioWorker;
        this.sessionManager = sessionManager;
        this.deployment = deployment;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registerWorkerThreadMetrics(registry);
        registerSessionMetrics(registry);
    }

    private void registerWorkerThreadMetrics(MeterRegistry registry) {
        Gauge.builder("undertow.threads.worker.core", xnioWorker,
                    w -> safeDouble(() -> w.getMXBean().getCoreWorkerPoolSize()))
                .tags(tags).baseUnit(BaseUnits.THREADS)
                .description("Core worker thread pool size")
                .register(registry);

        Gauge.builder("undertow.threads.worker.max", xnioWorker,
                    w -> safeDouble(() -> w.getMXBean().getMaxWorkerPoolSize()))
                .tags(tags).baseUnit(BaseUnits.THREADS)
                .description("Maximum worker thread pool size")
                .register(registry);

        Gauge.builder("undertow.threads.worker.current", xnioWorker,
                    w -> safeDouble(() -> w.getMXBean().getWorkerPoolSize()))
                .tags(tags).baseUnit(BaseUnits.THREADS)
                .description("Current worker thread count")
                .register(registry);

        // jboss-threads only tracks the active count when the
        // jboss.threads.eqe.statistics.active-count system property is true; it
        // returns -1 otherwise, which is reported as NaN rather than a bogus value.
        Gauge.builder("undertow.threads.worker.busy", xnioWorker,
                    w -> nonNegativeOrNaN(safeDouble(() -> w.getMXBean().getBusyWorkerThreadCount())))
                .tags(tags).baseUnit(BaseUnits.THREADS)
                .description("Busy worker thread count (requires "
                        + "-Djboss.threads.eqe.statistics.active-count=true)")
                .register(registry);

        Gauge.builder("undertow.threads.worker.queue.size", xnioWorker,
                    w -> safeDouble(() -> w.getMXBean().getWorkerQueueSize()))
                .tags(tags)
                .description("Worker thread queue size")
                .register(registry);

        Gauge.builder("undertow.threads.io", xnioWorker, XnioWorker::getIoThreadCount)
                .tags(tags).baseUnit(BaseUnits.THREADS)
                .description("IO thread count")
                .register(registry);
    }

    private void registerSessionMetrics(MeterRegistry registry) {
        if (sessionManager == null) {
            return;
        }

        Gauge.builder("undertow.sessions.active.current", sessionManager,
                    this::getActiveSessions)
                .tags(tags).baseUnit(BaseUnits.SESSIONS)
                .description("Current active sessions")
                .register(registry);

        Gauge.builder("undertow.sessions.active.max", sessionManager,
                    m -> (m.getStatistics() != null)
                            ? nonNegativeOrNaN(safeDouble(() -> m.getStatistics().getMaxActiveSessions()))
                            : Double.NaN)
                .tags(tags).baseUnit(BaseUnits.SESSIONS)
                .description("Maximum sessions allowed (NaN when unlimited)")
                .register(registry);

        FunctionCounter.builder("undertow.sessions.created", sessionManager,
                    m -> (m.getStatistics() != null)
                            ? safeDouble(() -> m.getStatistics().getCreatedSessionCount())
                            : Double.NaN)
                .tags(tags).baseUnit(BaseUnits.SESSIONS)
                .description("Total sessions created")
                .register(registry);

        FunctionCounter.builder("undertow.sessions.expired", sessionManager,
                    m -> (m.getStatistics() != null)
                            ? safeDouble(() -> m.getStatistics().getExpiredSessionCount())
                            : Double.NaN)
                .tags(tags).baseUnit(BaseUnits.SESSIONS)
                .description("Total sessions expired")
                .register(registry);
    }

    private double getActiveSessions(SessionManager manager) {
        if (manager.getStatistics() != null) {
            return safeDouble(() -> manager.getStatistics().getActiveSessionCount());
        }
        if (deployment != null && deployment.getSessionManager() != null) {
            return safeDouble(() -> deployment.getSessionManager().getActiveSessions().size());
        }
        return Double.NaN;
    }

    private static double nonNegativeOrNaN(double value) {
        return (value < 0) ? Double.NaN : value;
    }

    private double safeDouble(Supplier<Object> supplier) {
        try {
            Object result = supplier.get();
            if (result instanceof Number n) {
                return n.doubleValue();
            }
            return (result != null) ? Double.parseDouble(result.toString()) : Double.NaN;
        }
        catch (Exception ex) {
            LOG.trace(ex.getMessage(), ex);
            return Double.NaN;
        }
    }

    @Override
    public void close() {
    }

}
