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
import umrao.compile.service.util.HttpJsonResponder;

import java.io.IOException;

/**
 * Health check handler returning system liveness, uptime, and version.
 *
 * @author Vivek Umrao
 */
public class HealthHandler implements HttpHandler {

    private final long startTimeMs;

    public HealthHandler(long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpJsonResponder.sendError(exchange, 405, "Method Not Allowed. Use GET.");
            return;
        }

        long uptime = System.currentTimeMillis() - startTimeMs;
        String json = String.format("{\"status\":\"UP\",\"uptimeMs\":%d,\"version\":\"1.0.0\"}", uptime);
        HttpJsonResponder.sendJson(exchange, 200, json);
    }
}
