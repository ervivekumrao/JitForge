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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import umrao.compile.api.DynamicCompiler;
import umrao.compile.security.SandboxedExecutor;
import umrao.compile.service.util.HttpJsonResponder;
import umrao.compile.store.TieredBytecodeRegistry;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * REST endpoint handler for dynamically resolving and executing methods
 * on compiled classes stored across the tiered storage hierarchy within a sandboxed timeout.
 *
 * @author Vivek Umrao
 */
public class ExecuteHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(ExecuteHandler.class);

    private final DynamicCompiler compiler;
    private final TieredBytecodeRegistry registry;
    private final SandboxedExecutor sandboxedExecutor;

    public ExecuteHandler(DynamicCompiler compiler, TieredBytecodeRegistry registry) {
        this(compiler, registry, new SandboxedExecutor(Duration.ofSeconds(10)));
    }

    public ExecuteHandler(DynamicCompiler compiler, TieredBytecodeRegistry registry, SandboxedExecutor sandboxedExecutor) {
        this.compiler = compiler;
        this.registry = registry;
        this.sandboxedExecutor = sandboxedExecutor != null ? sandboxedExecutor : new SandboxedExecutor(Duration.ofSeconds(10));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpJsonResponder.sendError(exchange, 405, "Method Not Allowed. Use POST.");
            return;
        }

        String body = HttpJsonResponder.readBody(exchange);
        if (body == null || body.isBlank()) {
            HttpJsonResponder.sendError(exchange, 400, "Missing request body");
            return;
        }

        String className = HttpJsonResponder.extractJsonStringField(body, "className");
        String methodName = HttpJsonResponder.extractJsonStringField(body, "methodName");
        List<String> argsList = HttpJsonResponder.extractJsonStringArrayField(body, "args");
        if (argsList.isEmpty()) {
            argsList = HttpJsonResponder.extractJsonStringArrayField(body, "arguments");
        }

        if (className == null || className.isBlank()) {
            HttpJsonResponder.sendError(exchange, 400, "Missing 'className' field");
            return;
        }
        if (methodName == null || methodName.isBlank()) {
            HttpJsonResponder.sendError(exchange, 400, "Missing 'methodName' field");
            return;
        }

        try {
            Class<?> clazz;
            try {
                clazz = compiler.loadClass(className);
            } catch (ClassNotFoundException cnfe) {
                Optional<byte[]> bytecode = registry != null ? registry.findBytecode(className) : Optional.empty();
                if (bytecode.isPresent()) {
                    compiler.registerCompiledClass(className, bytecode.get());
                    clazz = compiler.loadClass(className);
                } else {
                    HttpJsonResponder.sendError(exchange, 404, "Class not found: " + className);
                    return;
                }
            }

            Method targetMethod = null;
            Object[] invocationArgs = null;

            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(methodName)) {
                    Class<?>[] paramTypes = m.getParameterTypes();
                    if (paramTypes.length == argsList.size()) {
                        try {
                            Object[] parsed = parseArgs(paramTypes, argsList);
                            targetMethod = m;
                            invocationArgs = parsed;
                            break;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            if (targetMethod == null) {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().equals(methodName)) {
                        Class<?>[] paramTypes = m.getParameterTypes();
                        if (paramTypes.length == argsList.size()) {
                            try {
                                Object[] parsed = parseArgs(paramTypes, argsList);
                                targetMethod = m;
                                invocationArgs = parsed;
                                break;
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }

            if (targetMethod == null) {
                HttpJsonResponder.sendError(exchange, 404, String.format(
                        "Method '%s' with %d arguments not found on class '%s'",
                        methodName, argsList.size(), className
                ));
                return;
            }

            Object targetInstance = null;
            if (!Modifier.isStatic(targetMethod.getModifiers())) {
                try {
                    var ctor = clazz.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    targetInstance = ctor.newInstance();
                } catch (NoSuchMethodException e) {
                    var constructors = clazz.getDeclaredConstructors();
                    if (constructors.length > 0) {
                        constructors[0].setAccessible(true);
                        Object[] defaultParams = new Object[constructors[0].getParameterCount()];
                        targetInstance = constructors[0].newInstance(defaultParams);
                    } else {
                        throw new IllegalStateException("No constructor found on " + className);
                    }
                }
            }

            final Object instance = targetInstance;
            final Method method = targetMethod;
            final Object[] methodArgs = invocationArgs;

            // Execute safely inside SandboxedExecutor with timeout protection
            Object output = sandboxedExecutor.execute(() -> {
                method.setAccessible(true);
                return method.invoke(instance, methodArgs);
            });

            String resultStr = output != null ? String.valueOf(output) : "null";

            String json = String.format(
                    "{\"success\":true,\"className\":\"%s\",\"methodName\":\"%s\",\"result\":\"%s\"}",
                    HttpJsonResponder.escapeJson(className),
                    HttpJsonResponder.escapeJson(methodName),
                    HttpJsonResponder.escapeJson(resultStr)
            );
            HttpJsonResponder.sendJson(exchange, 200, json);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
            log.error("Target method invocation failed: {}", cause.getMessage(), cause);
            HttpJsonResponder.sendError(exchange, 500, "Invocation error: " + cause.getMessage());
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Execution error: {}", cause.getMessage(), cause);
            HttpJsonResponder.sendError(exchange, 500, "Execution error: " + cause.getMessage());
        }
    }

    private static Object[] parseArgs(Class<?>[] paramTypes, List<String> strArgs) {
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> pt = paramTypes[i];
            String raw = strArgs.get(i);
            if (pt == String.class) {
                args[i] = raw;
            } else if (pt == int.class || pt == Integer.class) {
                args[i] = Integer.parseInt(raw);
            } else if (pt == long.class || pt == Long.class) {
                args[i] = Long.parseLong(raw);
            } else if (pt == double.class || pt == Double.class) {
                args[i] = Double.parseDouble(raw);
            } else if (pt == float.class || pt == Float.class) {
                args[i] = Float.parseFloat(raw);
            } else if (pt == boolean.class || pt == Boolean.class) {
                args[i] = Boolean.parseBoolean(raw);
            } else if (pt == short.class || pt == Short.class) {
                args[i] = Short.parseShort(raw);
            } else if (pt == byte.class || pt == Byte.class) {
                args[i] = Byte.parseByte(raw);
            } else {
                args[i] = raw;
            }
        }
        return args;
    }
}
