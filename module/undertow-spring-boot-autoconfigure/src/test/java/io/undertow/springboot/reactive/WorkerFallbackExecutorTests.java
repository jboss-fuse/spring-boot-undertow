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

package io.undertow.springboot.reactive;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import io.undertow.springboot.reactive.UndertowReactiveWebServerFactory.WorkerFallbackExecutor;
import org.junit.jupiter.api.Test;
import org.xnio.XnioWorker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link WorkerFallbackExecutor}.
 */
class WorkerFallbackExecutorTests {

    private final WorkerFallbackExecutor executor = new WorkerFallbackExecutor();

    @Test
    void delegatesToRunningWorker() {
        XnioWorker worker = mock(XnioWorker.class);
        this.executor.setWorker(worker);
        Runnable task = () -> { };
        this.executor.execute(task);
        then(worker).should().execute(task);
    }

    @Test
    void replacesWorkerThatHasBeenShutDown() {
        XnioWorker stopped = mock(XnioWorker.class);
        given(stopped.isShutdown()).willReturn(true);
        this.executor.setWorker(stopped);
        XnioWorker restarted = mock(XnioWorker.class);
        this.executor.setWorker(restarted);
        Runnable task = () -> { };
        this.executor.execute(task);
        then(restarted).should().execute(task);
    }

    @Test
    void keepsWorkerThatIsRunning() {
        XnioWorker running = mock(XnioWorker.class);
        this.executor.setWorker(running);
        this.executor.setWorker(mock(XnioWorker.class));
        Runnable task = () -> { };
        this.executor.execute(task);
        then(running).should().execute(task);
    }

    @Test
    void runsInlineWhenWorkerIsShutDown() {
        XnioWorker worker = mock(XnioWorker.class);
        given(worker.isShutdown()).willReturn(true);
        this.executor.setWorker(worker);
        assertThat(runAndCaptureThread()).isSameAs(Thread.currentThread());
        then(worker).should(org.mockito.Mockito.never()).execute(any());
    }

    @Test
    void runsInlineWhenWorkerRejects() {
        XnioWorker worker = mock(XnioWorker.class);
        willThrow(new RejectedExecutionException()).given(worker).execute(any());
        this.executor.setWorker(worker);
        assertThat(runAndCaptureThread()).isSameAs(Thread.currentThread());
    }

    @Test
    void runsInlineWhenNoWorkerHasBeenSeen() {
        assertThat(runAndCaptureThread()).isSameAs(Thread.currentThread());
    }

    @Test
    void swallowsRejectionFromInlineTaskDuringShutdown() {
        XnioWorker worker = mock(XnioWorker.class);
        given(worker.isShutdown()).willReturn(true);
        this.executor.setWorker(worker);
        this.executor.execute(() -> {
            throw new RejectedExecutionException("Thread is terminating");
        });
    }

    private Thread runAndCaptureThread() {
        AtomicReference<Thread> thread = new AtomicReference<>();
        this.executor.execute(() -> thread.set(Thread.currentThread()));
        return thread.get();
    }

}
