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
package umrao.compile.engine;

import javax.tools.SimpleJavaFileObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Objects;

/**
 * In-memory compiled bytecode container supporting both read and write operations.
 *
 * @author Vivek Umrao
 */
public class BytecodeJavaFileObject extends SimpleJavaFileObject {

    private final String className;
    private final ByteArrayOutputStream outputStream;
    private final byte[] byteCode;

    /**
     * Creates an output-oriented file object for compiler to write bytecode into.
     */
    public BytecodeJavaFileObject(String className) {
        super(URI.create("mem:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        this.className = Objects.requireNonNull(className, "className must not be null");
        this.outputStream = new ByteArrayOutputStream();
        this.byteCode = null;
    }

    /**
     * Creates an input-oriented file object initialized with existing bytecode bytes.
     */
    public BytecodeJavaFileObject(String className, byte[] byteCode) {
        super(URI.create("mem:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        this.className = Objects.requireNonNull(className, "className must not be null");
        this.outputStream = null;
        this.byteCode = Objects.requireNonNull(byteCode, "byteCode must not be null");
    }

    public String getClassName() {
        return className;
    }

    @Override
    public OutputStream openOutputStream() {
        if (outputStream == null) {
            throw new UnsupportedOperationException("BytecodeJavaFileObject was created in read-only mode for " + className);
        }
        return outputStream;
    }

    @Override
    public InputStream openInputStream() {
        byte[] bytes = getByteCode();
        if (bytes == null) {
            throw new IllegalStateException("No bytecode available for " + className);
        }
        return new ByteArrayInputStream(bytes);
    }

    public byte[] getByteCode() {
        if (byteCode != null) {
            return byteCode;
        }
        return outputStream != null ? outputStream.toByteArray() : null;
    }
}
