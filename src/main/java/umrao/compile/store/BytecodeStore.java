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
package umrao.compile.store;

import java.util.Map;
import java.util.Optional;

/**
 * Storage provider interface for persisting and retrieving compiled class bytecodes
 * across tiered locations (e.g. In-Memory Cache/Redis, Local Filesystem, External DB).
 *
 * @author Vivek Umrao
 */
public interface BytecodeStore {

    /**
     * Identifies the storage tier (e.g., "REDIS", "FILESYSTEM", "DATABASE").
     */
    String getStorageType();

    /**
     * Persists bytecode for a specific fully-qualified class name.
     */
    void save(String className, byte[] bytecode);

    /**
     * Retrieves bytecode for a class if present in this storage tier.
     */
    Optional<byte[]> load(String className);

    /**
     * Checks whether a class exists in this storage tier.
     */
    boolean contains(String className);

    /**
     * Discovers and retrieves all class bytecodes belonging to a given package.
     */
    Map<String, byte[]> loadPackage(String packageName, boolean recurse);

    /**
     * Clears all stored bytecode in this storage tier.
     */
    default void clear() {
    }
}
