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
package umrao.compile.service.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import umrao.compile.api.DynamicCompiler;
import umrao.compile.service.util.HttpJsonResponder;
import umrao.compile.store.TieredBytecodeRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;
import java.util.Optional;

/**
 * REST endpoint handler for serving compiled class bytecodes.
 * Supports:
 * 1. Raw JVM bytecode binary stream (for standard URLClassLoader and direct class definition).
 * 2. Base64 JSON format (when requested with Accept: application/json).
 *
 * @author Vivek Umrao
 */
public class BytecodeHandler implements HttpHandler {

    private final DynamicCompiler compiler;
    private final TieredBytecodeRegistry registry;

    public BytecodeHandler(DynamicCompiler compiler, TieredBytecodeRegistry registry) {
        this.compiler = compiler;
        this.registry = registry;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpJsonResponder.sendError(exchange, 405, "Method Not Allowed. Use GET.");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String className = extractClassName(path);

        if (className == null || className.isBlank()) {
            HttpJsonResponder.sendError(exchange, 400, "Missing class name in URL path. Example: /api/bytecode/com.example.MyClass");
            return;
        }

        byte[] bytecode = compiler.getCompiledBytecode(className);
        if (bytecode == null && registry != null) {
            Optional<byte[]> found = registry.findBytecode(className);
            if (found.isPresent()) {
                bytecode = found.get();
            }
        }

        if (bytecode == null) {
            HttpJsonResponder.sendError(exchange, 404, "Bytecode not found for class: " + className);
            return;
        }

        String accept = exchange.getRequestHeaders().getFirst("Accept");
        if (accept != null && accept.contains("application/json")) {
            String b64 = Base64.getEncoder().encodeToString(bytecode);
            String json = String.format(
                    "{\"success\":true,\"className\":\"%s\",\"bytecode\":\"%s\"}",
                    HttpJsonResponder.escapeJson(className), b64
            );
            HttpJsonResponder.sendJson(exchange, 200, json);
        } else {
            exchange.getResponseHeaders().set("Content-Type", "application/java-vm-type");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(bytecode.length));
            exchange.sendResponseHeaders(200, bytecode.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytecode);
            }
        }
    }

    public static String extractClassName(String path) {
        if (path == null) return null;

        String stripped = path;
        if (stripped.startsWith("/api/bytecode/")) {
            stripped = stripped.substring("/api/bytecode/".length());
        } else if (stripped.startsWith("/api/classes/")) {
            stripped = stripped.substring("/api/classes/".length());
        } else if (stripped.startsWith("/api/bytecode")) {
            stripped = stripped.substring("/api/bytecode".length());
        } else if (stripped.startsWith("/api/classes")) {
            stripped = stripped.substring("/api/classes".length());
        }

        if (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }

        if (stripped.endsWith(".class")) {
            stripped = stripped.substring(0, stripped.length() - 6);
        }

        stripped = stripped.replace('/', '.');
        return stripped.trim();
    }
}
