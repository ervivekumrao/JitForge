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
package umrao.compile.jit;

import org.codehaus.commons.compiler.util.resource.MapResourceCreator;
import org.codehaus.commons.compiler.util.resource.Resource;
import org.codehaus.commons.compiler.util.resource.StringResource;
import org.codehaus.janino.Compiler;
import org.codehaus.janino.ExpressionEvaluator;
import org.codehaus.janino.SimpleCompiler;
import umrao.compile.engine.JavaSourceParser;

import java.util.HashMap;
import java.util.Map;

/**
 * Janino Embedded JIT Compiler:
 * Ultra-fast embedded compiler widely used in big-data engines (Apache Spark, Presto, Calcite)
 * for runtime query JIT generation and high-throughput class compilation.
 *
 * @author Vivek Umrao
 */
@SuppressWarnings("unused")
public class JaninoJitCompiler {

    /**
     * Compiles Java source code directly into JVM bytecode taking only the source code input.
     */
    public Object compileAndInstantiate(String sourceCode) {
        String className = JavaSourceParser.parseFullClassName(sourceCode);
        return compileAndInstantiate(className, sourceCode, Object.class);
    }

    /**
     * Compiles Java source code into bytecode in-memory and returns an instantiated object.
     */
    public Object compileAndInstantiate(String className, String sourceCode) {
        return compileAndInstantiate(className, sourceCode, Object.class);
    }

    /**
     * Compiles Java source code directly into JVM bytecode in-memory in milliseconds
     * and validates/casts to targetInterface.
     */
    @SuppressWarnings("unchecked")
    public <T> T compileAndInstantiate(String className, String sourceCode, Class<T> targetInterface) {
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            ClassLoader parent = targetInterface != null && targetInterface.getClassLoader() != null
                    ? targetInterface.getClassLoader()
                    : Thread.currentThread().getContextClassLoader();
            compiler.setParentClassLoader(parent != null ? parent : getClass().getClassLoader());
            compiler.cook(sourceCode);

            Class<?> clazz = compiler.getClassLoader().loadClass(className);
            return (T) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Janino runtime compilation failed for " + className, e);
        }
    }

    /**
     * Compiles Java source code and returns the loaded Class object.
     */
    public Class<?> compile(String className, String sourceCode) {
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            ClassLoader parent = Thread.currentThread().getContextClassLoader();
            compiler.setParentClassLoader(parent != null ? parent : getClass().getClassLoader());
            compiler.cook(sourceCode);
            return compiler.getClassLoader().loadClass(className);
        } catch (Exception e) {
            throw new RuntimeException("Janino runtime compilation failed for " + className, e);
        }
    }

    /**
     * Compiles Java source code taking only the source code and returns the loaded Class object.
     */
    public Class<?> compile(String sourceCode) {
        String className = JavaSourceParser.parseFullClassName(sourceCode);
        return compile(className, sourceCode);
    }

    /**
     * Compiles Java source code into raw JVM bytecode entirely in RAM.
     * Returns a map of class name to bytecode bytes with zero disk I/O.
     */
    public Map<String, byte[]> compileToBytecodes(String className, String sourceCode) {
        try {
            Map<String, byte[]> rawFiles = new HashMap<>();
            Compiler compiler = new Compiler();
            compiler.setDestinationDirectory(null, false);
            compiler.setClassFileCreator(new MapResourceCreator(rawFiles));

            String path = className.replace('.', '/') + ".java";
            compiler.compile(new Resource[]{
                    new StringResource(path, sourceCode)
            });

            Map<String, byte[]> normalized = new HashMap<>();
            for (Map.Entry<String, byte[]> entry : rawFiles.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith(".class")) {
                    key = key.substring(0, key.length() - 6);
                }
                key = key.replace('/', '.');
                normalized.put(key, entry.getValue());
            }
            return normalized;
        } catch (Exception e) {
            throw new RuntimeException("Janino runtime compilation failed for " + className + ": " + e.getMessage(), e);
        }
    }

    /**
     * Evaluates a dynamic Java expression in-memory without compiling a full class.
     * Example: evaluateExpression("x * 2 + y", Integer.class, new String[]{"x", "y"}, new Class[]{int.class, int.class}, new Object[]{10, 5}) -> 25
     */
    public Object evaluateExpression(String expression,
                                     Class<?> returnType,
                                     String[] parameterNames,
                                     Class<?>[] parameterTypes,
                                     Object[] argumentValues) {
        try {
            ExpressionEvaluator evaluator = new ExpressionEvaluator();
            evaluator.setParameters(parameterNames, parameterTypes);
            evaluator.setExpressionType(returnType);
            evaluator.cook(expression);
            return evaluator.evaluate(argumentValues);
        } catch (Exception e) {
            throw new RuntimeException("Janino expression evaluation failed for: " + expression, e);
        }
    }
}
