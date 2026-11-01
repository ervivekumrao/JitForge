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
import umrao.compile.cache.CompilationCache;
import umrao.compile.cache.LruCompilationCache;
import umrao.std.Message;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CompilationCacheTest {

    @Test
    @DisplayName("Default constructor should initialize cache with default capacity")
    void testDefaultConstructor() {
        CompilationCache defaultCache = new LruCompilationCache();
        assertThat(defaultCache.size()).isZero();
    }

    @Test
    @DisplayName("Cache should evict least recently used entries when capacity is exceeded")
    void testLruEviction() {
        CompilationCache cache = new LruCompilationCache(2);

        CompilationResult<Message> res1 = CompilationResult.success(Message.class, Collections.emptyList(), 10, false);
        CompilationResult<Message> res2 = CompilationResult.success(Message.class, Collections.emptyList(), 10, false);
        CompilationResult<Message> res3 = CompilationResult.success(Message.class, Collections.emptyList(), 10, false);

        cache.put("key1", res1);
        cache.put("key2", res2);
        assertThat(cache.size()).isEqualTo(2);

        // Access key1 so key2 becomes least recently used
        assertThat(cache.get("key1", Message.class)).isPresent();

        // Add key3, triggering eviction of key2
        cache.put("key3", res3);
        assertThat(cache.size()).isEqualTo(2);

        assertThat(cache.get("key1", Message.class)).isPresent();
        assertThat(cache.get("key3", Message.class)).isPresent();
        assertThat(cache.get("key2", Message.class)).isEmpty();
    }

    @Test
    @DisplayName("Cache invalidateAll should clear all stored entries")
    void testInvalidateAll() {
        CompilationCache cache = new LruCompilationCache(10);
        CompilationResult<Message> res = CompilationResult.success(Message.class, Collections.emptyList(), 5, false);

        cache.put("k1", res);
        cache.put("k2", res);
        assertThat(cache.size()).isEqualTo(2);

        cache.invalidateAll();
        assertThat(cache.size()).isZero();
        assertThat(cache.get("k1", Message.class)).isEmpty();
    }

    @Test
    @DisplayName("Cache key computation should produce deterministic SHA-256 digests")
    void testDeterministicKeyComputation() {
        String key1 = LruCompilationCache.computeKey("MyClass", "class MyClass {}");
        String key2 = LruCompilationCache.computeKey("MyClass", "class MyClass {}");
        String keyDiff = LruCompilationCache.computeKey("MyClass", "class MyClass { int x; }");

        assertThat(key1).isNotNull().isEqualTo(key2);
        assertThat(key1).isNotEqualTo(keyDiff);
        assertThat(key1).hasSize(64); // SHA-256 produces 64 hex characters
    }

    @Test
    @DisplayName("Cache should reject retrieval when requested type does not match stored class")
    void testTypeMismatchHandling() {
        CompilationCache cache = new LruCompilationCache(5);
        CompilationResult<String> stringResult = CompilationResult.success(String.class, Collections.emptyList(), 5, false);
        cache.put("strKey", stringResult);

        // Attempt to retrieve String class expecting Message interface
        Optional<CompilationResult<Message>> retrieved = cache.get("strKey", Message.class);
        assertThat(retrieved).isEmpty();
    }
}
