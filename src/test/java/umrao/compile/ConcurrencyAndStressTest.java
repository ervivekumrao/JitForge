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
package umrao.compile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import umrao.compile.api.CompilationResult;
import umrao.compile.api.DynamicCompiler;
import umrao.compile.engine.InMemoryCompiler;
import umrao.std.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyAndStressTest {

    @Test
    @DisplayName("Should successfully handle concurrent compilations across multiple threads without race conditions")
    @SuppressWarnings("resource")
    void testConcurrentCompilationStress() throws Exception {
        DynamicCompiler compiler = new InMemoryCompiler();
        int threadCount = 8;
        int tasksPerThread = 5;
        int totalTasks = threadCount * tasksPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            List<Future<CompilationResult<Message>>> futures = new ArrayList<>();

            for (int i = 0; i < totalTasks; i++) {
                final int id = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await(); // Synchronize all threads to start compiling at the exact same moment

                    String className = "umrao.generated.StressClass" + id;
                    String code = String.format("""
                            package umrao.generated;
                            import umrao.std.Message;
                            
                            /**
 * @author Vivek Umrao
 */
public class StressClass%d implements Message {
                                private final int id = %d;
                                @Override
                                public void showMessage() {
                                    System.out.println("Stress class " + id);
                                }
                            }
                            """, id, id);

                    return compiler.compile(className, code, Message.class);
                }));
            }

            // Wait until all worker threads are created and ready
            boolean ready = readyLatch.await(5, TimeUnit.SECONDS);
            assertThat(ready).isTrue();

            // Release the barrier
            startLatch.countDown();

            for (Future<CompilationResult<Message>> future : futures) {
                CompilationResult<Message> result = future.get(30, TimeUnit.SECONDS);
                assertThat(result).isNotNull();
                assertThat(result.isSuccess()).isTrue();
                Message instance = result.newInstance();
                assertThat(instance).isNotNull();
            }
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
