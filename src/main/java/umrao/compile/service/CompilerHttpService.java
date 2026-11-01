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
package umrao.compile.service;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import umrao.compile.api.DynamicCompiler;
import umrao.compile.engine.InMemoryCompiler;
import umrao.compile.security.SecurityPolicy;
import umrao.compile.service.config.CompilerServiceConfig;
import umrao.compile.service.handler.BytecodeHandler;
import umrao.compile.service.handler.CompileHandler;
import umrao.compile.service.handler.ExecuteHandler;
import umrao.compile.service.handler.HealthHandler;
import umrao.compile.service.handler.TiersStatusHandler;
import umrao.compile.store.BytecodeStore;
import umrao.compile.store.DatabaseBytecodeStore;
import umrao.compile.store.FileSystemBytecodeStore;
import umrao.compile.store.RedisBytecodeStore;
import umrao.compile.store.TieredBytecodeRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enterprise HTTP REST microservice orchestrating dynamic Java compilation,
 * reflection-free execution, bytecode distribution, and 4-tier storage (ClassLoader &gt; Redis &gt; Filesystem &gt; DB).
 * <p>
 * Adheres to SOLID design principles:
 * - Single Responsibility: routing and lifecycle management only.
 * - Dependency Inversion: depends on DynamicCompiler and TieredBytecodeRegistry abstractions.
 * - 12-Factor App: configurable via CompilerServiceConfig.
 *
 * @author Vivek Umrao
 */
@SuppressWarnings("unused")
public class CompilerHttpService {

    private static final Logger log = LoggerFactory.getLogger(CompilerHttpService.class);

    private final int port;
    private final DynamicCompiler compiler;
    private final TieredBytecodeRegistry registry;
    private final long startTimeMs;

    private HttpServer server;
    private ExecutorService executor;

    public CompilerHttpService(int port, DynamicCompiler compiler, TieredBytecodeRegistry registry) {
        this.port = port;
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.startTimeMs = System.currentTimeMillis();
    }

    public static CompilerHttpService fromEnvironment() {
        return fromConfig(CompilerServiceConfig.fromEnvironment());
    }

    public static CompilerHttpService fromConfig(CompilerServiceConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        // 1. Filesystem Store (Tier 3) with graceful fallback
        BytecodeStore fileSystemStore;
        try {
            fileSystemStore = new FileSystemBytecodeStore(config.storageDir());
        } catch (Exception e) {
            log.warn("Failed initializing filesystem store at {} - falling back to build/compiler-storage: {}",
                    config.storageDir(), e.getMessage());
            fileSystemStore = new FileSystemBytecodeStore(Path.of("build/compiler-storage"));
        }

        // 2. Redis Store (Tier 2)
        BytecodeStore redisStore;
        if (config.redisHost() != null && !config.redisHost().isBlank()) {
            try {
                redisStore = new RedisBytecodeStore(
                        config.redisHost(), config.redisPort(), config.redisPassword(), "runtime:bytecode:"
                );
                log.info("Initialized Redis cache at {}:{}", config.redisHost(), config.redisPort());
            } catch (Exception e) {
                log.warn("Failed connecting to Redis at {}:{} - using in-memory fallback",
                        config.redisHost(), config.redisPort(), e);
                redisStore = RedisBytecodeStore.inMemory();
            }
        } else {
            redisStore = RedisBytecodeStore.inMemory();
        }

        // 3. Database Store (Tier 4)
        BytecodeStore dbStore;
        if (config.dbUrl() != null && !config.dbUrl().isBlank()) {
            try {
                dbStore = new DatabaseBytecodeStore(config.dbUrl(), config.dbUser(), config.dbPassword());
                log.info("Initialized SQL database store at {}", config.dbUrl());
            } catch (Exception e) {
                log.warn("Failed connecting to database at {} - using in-memory H2 fallback", config.dbUrl(), e);
                dbStore = DatabaseBytecodeStore.h2InMemory("service_bytecode_db");
            }
        } else {
            dbStore = DatabaseBytecodeStore.h2InMemory("service_bytecode_db");
        }

        SecurityPolicy effectivePolicy = config.sandboxEnabled() ? SecurityPolicy.strict() : SecurityPolicy.permissive();

        DynamicCompiler dynamicCompiler = InMemoryCompiler.builder()
                .withSecurityPolicy(effectivePolicy)
                .withRedisStore(redisStore)
                .withFileSystemStore(fileSystemStore)
                .withDatabaseStore(dbStore)
                .build();

        return new CompilerHttpService(config.port(), dynamicCompiler, dynamicCompiler.getTieredRegistry());
    }

    /**
     * Starts the HTTP server and registers endpoints.
     */
    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }

        executor = Executors.newVirtualThreadPerTaskExecutor();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(executor);

        // Register REST API endpoints
        server.createContext("/api/health", new HealthHandler(startTimeMs));
        server.createContext("/api/tiers", new TiersStatusHandler(registry));
        server.createContext("/api/compile", new CompileHandler(compiler));
        server.createContext("/api/execute", new ExecuteHandler(compiler, registry));
        server.createContext("/api/bytecode", new BytecodeHandler(compiler, registry));
        server.createContext("/api/classes", new BytecodeHandler(compiler, registry));

        server.start();
        log.info("JitForge HTTP service started on port {}", server.getAddress().getPort());
    }

    /**
     * Gracefully stops the HTTP server and shuts down executor threads.
     */
    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        if (registry != null && registry.getRedisStore() instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
        log.info("JitForge HTTP service stopped.");
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public int getPort() {
        if (server != null) {
            return server.getAddress().getPort();
        }
        return port;
    }

    public DynamicCompiler getCompiler() {
        return compiler;
    }

    public TieredBytecodeRegistry getRegistry() {
        return registry;
    }
}
