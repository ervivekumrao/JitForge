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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Persists and resolves compiled class bytecodes directly on the local filesystem of the compiler pod
 * with strict path traversal boundary verification.
 *
 * @author Vivek Umrao
 */
public class FileSystemBytecodeStore implements BytecodeStore {

    private static final Logger log = LoggerFactory.getLogger(FileSystemBytecodeStore.class);
    private final Path rootDir;

    public FileSystemBytecodeStore(Path rootDir) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir must not be null").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize filesystem bytecode store at: " + rootDir, e);
        }
    }

    public FileSystemBytecodeStore(File rootDir) {
        this(rootDir != null ? rootDir.toPath() : null);
    }

    @Override
    public String getStorageType() {
        return "FILESYSTEM";
    }

    @SuppressWarnings("unused")
    public Path getRootDir() {
        return rootDir;
    }

    @Override
    public void save(String className, byte[] bytecode) {
        if (className == null || bytecode == null) {
            return;
        }
        try {
            Path targetFile = resolveClassFilePath(className);
            Files.createDirectories(targetFile.getParent());
            Files.write(targetFile, bytecode);
            log.debug("Saved bytecode for {} to filesystem: {}", className, targetFile);
        } catch (IOException e) {
            log.error("Failed saving bytecode to filesystem for class {}", className, e);
            throw new RuntimeException("Failed saving bytecode for " + className, e);
        }
    }

    @Override
    public Optional<byte[]> load(String className) {
        if (className == null) {
            return Optional.empty();
        }
        try {
            Path targetFile = resolveClassFilePath(className);
            if (Files.isRegularFile(targetFile)) {
                return Optional.of(Files.readAllBytes(targetFile));
            }
        } catch (Exception e) {
            log.warn("Failed reading bytecode from filesystem for class {}: {}", className, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public boolean contains(String className) {
        if (className == null) {
            return false;
        }
        try {
            return Files.isRegularFile(resolveClassFilePath(className));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Map<String, byte[]> loadPackage(String packageName, boolean recurse) {
        Path packageDir;
        try {
            packageDir = resolvePackageDirPath(packageName);
        } catch (Exception e) {
            log.warn("Invalid package path for {}: {}", packageName, e.getMessage());
            return Collections.emptyMap();
        }

        if (!Files.isDirectory(packageDir)) {
            return Collections.emptyMap();
        }

        Map<String, byte[]> result = new HashMap<>();
        int maxDepth = recurse ? Integer.MAX_VALUE : 1;

        try (Stream<Path> stream = Files.walk(packageDir, maxDepth)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class"))
                    .forEach(path -> {
                        try {
                            Path relative = rootDir.relativize(path);
                            String classPathStr = relative.toString().replace(File.separatorChar, '.');
                            String className = classPathStr.substring(0, classPathStr.length() - 6);
                            byte[] bytes = Files.readAllBytes(path);
                            result.put(className, bytes);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed walking package directory for {}: {}", packageName, e.getMessage());
        }

        return result;
    }

    @Override
    public void clear() {
        try (Stream<Path> stream = Files.walk(rootDir)) {
            stream.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(rootDir))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private Path resolveClassFilePath(String className) {
        String relative = className.replace('.', File.separatorChar) + ".class";
        Path target = rootDir.resolve(relative).normalize();
        if (!target.startsWith(rootDir)) {
            throw new SecurityException("Path traversal attempt detected in class name: " + className);
        }
        return target;
    }

    private Path resolvePackageDirPath(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return rootDir;
        }
        Path target = rootDir.resolve(packageName.replace('.', File.separatorChar)).normalize();
        if (!target.startsWith(rootDir)) {
            throw new SecurityException("Path traversal attempt detected in package name: " + packageName);
        }
        return target;
    }
}
