/*
 * JitForge - Enterprise Runtime Bytecode & JIT Compilation Engine
 *
 * Copyright (c) 2024-2026 Vivek Umrao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package umrao.compile.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes dynamic code within an isolated worker thread with strict timeout enforcement
 * to prevent DoS from infinite loops or CPU exhaustion.
 *
 * @author Vivek Umrao
 */
public class SandboxedExecutor {

    private static final Logger log = LoggerFactory.getLogger(SandboxedExecutor.class);

    private final ExecutorService executorService;
    private final Duration defaultTimeout;

    public SandboxedExecutor() {
        this(Duration.ofSeconds(5));
    }

    public SandboxedExecutor(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "sandboxed-dynamic-runner");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Executes a callable with the default timeout.
     */
    public <V> V execute(Callable<V> task) throws Exception {
        return execute(task, defaultTimeout);
    }

    /**
     * Executes a callable with a custom timeout.
     */
    public <V> V execute(Callable<V> task, Duration timeout) throws Exception {
        Future<V> future = executorService.submit(task);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Task execution timed out after {}ms and was interrupted", timeout.toMillis());
            throw new SecurityViolationException("EXECUTION_TIMEOUT",
                    "Dynamic execution timed out after " + timeout.toMillis() + "ms. Interrupted.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException("Execution failed", cause);
        }
    }

    /**
     * Executes a runnable task with timeout.
     */
    public void execute(Runnable task, Duration timeout) throws Exception {
        execute(() -> {
            task.run();
            return null;
        }, timeout);
    }

    public void shutdown() {
        executorService.shutdownNow();
    }
}
