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

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.tools.GroovyClass;

import java.util.HashMap;
import java.util.Map;

/**
 * Apache Groovy JIT Engine:
 * Compiles dynamic script source into native JVM bytecode on the fly.
 * Supports @groovy.transform.CompileStatic for static JIT performance.
 *
 * @author Vivek Umrao
 */
@SuppressWarnings("unused")
public class GroovyJitCompiler {

    private final GroovyClassLoader classLoader;

    public GroovyJitCompiler() {
        this.classLoader = new GroovyClassLoader(GroovyJitCompiler.class.getClassLoader());
    }

    /**
     * Compiles a dynamic Groovy script into bytecode and instantiates it without requiring an interface.
     */
    public Object compileAndInstantiate(String scriptCode) {
        try {
            Class<?> parsedClass = classLoader.parseClass(scriptCode);
            return parsedClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Groovy JIT compilation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Compiles a Groovy script implementing targetInterface into static JVM bytecode and instantiates it.
     */
    @SuppressWarnings("unchecked")
    public <T> T compileAndInstantiate(String scriptCode, Class<T> targetInterface) {
        try {
            Class<?> parsedClass = classLoader.parseClass(scriptCode);
            if (targetInterface != null && !targetInterface.isAssignableFrom(parsedClass)) {
                throw new IllegalArgumentException("Groovy script does not implement " + targetInterface.getName());
            }
            return (T) parsedClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Groovy JIT compilation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Compiles a Groovy script into a loaded Class object.
     */
    public Class<?> compile(String scriptCode) {
        try {
            return classLoader.parseClass(scriptCode);
        } catch (Exception e) {
            throw new RuntimeException("Groovy JIT compilation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Compiles a Groovy script into raw JVM bytecodes in RAM without writing to disk.
     */
    public Map<String, byte[]> compileToBytecodes(String className, String scriptCode) {
        try {
            CompilerConfiguration config = new CompilerConfiguration();
            CompilationUnit cu = new CompilationUnit(config, null, classLoader);
            cu.addSource(className != null ? className : "Script", scriptCode);
            cu.compile(Phases.CLASS_GENERATION);

            Map<String, byte[]> bytecodes = new HashMap<>();
            for (GroovyClass gc : cu.getClasses()) {
                bytecodes.put(gc.getName(), gc.getBytes());
            }
            return bytecodes;
        } catch (Exception e) {
            throw new RuntimeException("Groovy JIT compilation failed: " + e.getMessage(), e);
        }
    }
}
