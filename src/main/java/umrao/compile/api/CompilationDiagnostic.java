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

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Immutable value object representing a diagnostic message (error, warning, note)
 * produced by the Java compiler.
 *
 * @author Vivek Umrao
 */
public record CompilationDiagnostic(
        Diagnostic.Kind kind,
        long lineNumber,
        long columnNumber,
        String code,
        String message
) {
    public static CompilationDiagnostic from(Diagnostic<? extends JavaFileObject> diagnostic) {
        return new CompilationDiagnostic(
                diagnostic.getKind(),
                diagnostic.getLineNumber(),
                diagnostic.getColumnNumber(),
                diagnostic.getCode() != null ? diagnostic.getCode() : "UNKNOWN",
                diagnostic.getMessage(null)
        );
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public String toString() {
        return String.format("[%s] Line %d:%d - %s (%s)",
                kind, lineNumber, columnNumber, message, code);
    }
}
