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
package umrao.compile.cache;

import umrao.compile.api.CompilationResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe LRU in-memory cache for dynamic compilation results.
 *
 * @author Vivek Umrao
 */
public class LruCompilationCache implements CompilationCache {

    private final int capacity;
    private final Map<String, CompilationResult<?>> cacheMap;

    public LruCompilationCache() {
        this(100);
    }

    public LruCompilationCache(int capacity) {
        this.capacity = capacity;
        this.cacheMap = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CompilationResult<?>> eldest) {
                return size() > LruCompilationCache.this.capacity;
            }
        };
    }

    public static String computeKey(String className, String sourceCode) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(className.getBytes(StandardCharsets.UTF_8));
            md.update((byte) ':');
            md.update(sourceCode.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            return className + ":" + sourceCode.hashCode();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <T> Optional<CompilationResult<T>> get(String key, Class<T> expectedType) {
        CompilationResult<?> result = cacheMap.get(key);
        if (result != null && result.getCompiledClass() != null && expectedType.isAssignableFrom(result.getCompiledClass())) {
            // Return cached wrapper with cached flag = true
            return Optional.of(new CompilationResult<>(
                    result.isSuccess(),
                    (Class<? extends T>) result.getCompiledClass(),
                    result.getDiagnostics(),
                    0,
                    true
            ));
        }
        return Optional.empty();
    }

    @Override
    public synchronized <T> void put(String key, CompilationResult<T> result) {
        if (result != null && result.isSuccess()) {
            cacheMap.put(key, result);
        }
    }

    @Override
    public synchronized void invalidateAll() {
        cacheMap.clear();
    }

    @Override
    public synchronized int size() {
        return cacheMap.size();
    }
}
