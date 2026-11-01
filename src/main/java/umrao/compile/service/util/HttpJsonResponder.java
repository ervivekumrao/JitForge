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
package umrao.compile.service.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for reading HTTP payloads and writing standardized RFC 8259 JSON responses
 * with size boundary checks and control character escaping.
 *
 * @author Vivek Umrao
 */
public final class HttpJsonResponder {

    public static final int MAX_BODY_BYTES = 10 * 1024 * 1024; // 10MB protection against OOM

    private HttpJsonResponder() {
    }

    public static void sendJson(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String json = String.format("{\"success\":false,\"error\":\"%s\"}", escapeJson(message));
        sendJson(exchange, statusCode, json);
    }

    public static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int read;
            int total = 0;
            while ((read = is.read(buf)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException("Request payload exceeds maximum permitted size of " + MAX_BODY_BYTES + " bytes");
                }
                baos.write(buf, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String extractJsonStringField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return null;
    }

    public static List<String> extractJsonStringArrayField(String json, String field) {
        List<String> items = new ArrayList<>();
        Pattern arrayPattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\\[([^]]*)]");
        Matcher arrayMatcher = arrayPattern.matcher(json);
        if (arrayMatcher.find()) {
            String inner = arrayMatcher.group(1).trim();
            if (!inner.isEmpty()) {
                Pattern itemPattern = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"|([0-9.-]+|true|false)");
                Matcher itemMatcher = itemPattern.matcher(inner);
                while (itemMatcher.find()) {
                    if (itemMatcher.group(1) != null) {
                        items.add(unescapeJson(itemMatcher.group(1)));
                    } else if (itemMatcher.group(2) != null) {
                        items.add(itemMatcher.group(2));
                    }
                }
            }
        }
        return items;
    }

    public static String unescapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (i + 4 < s.length()) {
                            try {
                                String hex = s.substring(i + 1, i + 5);
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append("\\u");
                            }
                        } else {
                            sb.append("\\u");
                        }
                    }
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
