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
package umrao.compile.api;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when dynamic Java source code compilation fails.
 *
 * @author Vivek Umrao
 */
public class CompilationException extends RuntimeException {

    private final List<CompilationDiagnostic> diagnostics;

    public CompilationException(String message, List<CompilationDiagnostic> diagnostics) {
        super(buildErrorMessage(message, diagnostics));
        this.diagnostics = diagnostics != null ? Collections.unmodifiableList(diagnostics) : Collections.emptyList();
    }

    public CompilationException(String message, Throwable cause) {
        super(message, cause);
        this.diagnostics = Collections.emptyList();
    }

    public List<CompilationDiagnostic> getDiagnostics() {
        return diagnostics;
    }

    private static String buildErrorMessage(String baseMessage, List<CompilationDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return baseMessage;
        }
        StringBuilder sb = new StringBuilder(baseMessage).append(":\n");
        for (CompilationDiagnostic d : diagnostics) {
            sb.append("  -> ").append(d.toString()).append("\n");
        }
        return sb.toString();
    }
}
