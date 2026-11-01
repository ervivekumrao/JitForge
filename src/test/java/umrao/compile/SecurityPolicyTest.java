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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import umrao.compile.api.DynamicCompiler;
import umrao.compile.engine.InMemoryCompiler;
import umrao.compile.security.CodeSecurityValidator;
import umrao.compile.security.SecurityPolicy;
import umrao.compile.security.SecurityViolationException;
import umrao.std.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests verifying comprehensive security sandboxing, AST rules, and threat mitigation.
 *
 * @author Vivek Umrao
 */
class SecurityPolicyTest {

    @ParameterizedTest(name = "Strict policy should block import: {0}")
    @ValueSource(strings = {
            "java.lang.reflect.Method",
            "java.lang.reflect.Field",
            "sun.misc.Unsafe",
            "jdk.internal.misc.Unsafe",
            "java.net.Socket",
            "java.net.URL",
            "java.io.File",
            "java.nio.file.Paths"
    })
    @DisplayName("Should reject restricted imports in dynamic code")
    void testBlockedImports(String restrictedImport) {
        CodeSecurityValidator validator = new CodeSecurityValidator(SecurityPolicy.strict());
        String code = "package umrao.test;\nimport " + restrictedImport + ";\npublic class Tmp {}";

        assertThatThrownBy(() -> validator.validate(code))
                .isInstanceOf(SecurityViolationException.class)
                .satisfies(e -> {
                    SecurityViolationException sve = (SecurityViolationException) e;
                    assertThat(sve.getViolationRule()).isIn("BLOCKED_IMPORT", "RESTRICTED_CLASS_USAGE");
                });
    }

    @Test
    @DisplayName("Should reject static imports of restricted packages")
    void testBlockedStaticImports() {
        CodeSecurityValidator validator = new CodeSecurityValidator(SecurityPolicy.strict());
        String code = "package umrao.test;\nimport static java.lang.reflect.Method.*;\npublic class Tmp {}";

        assertThatThrownBy(() -> validator.validate(code))
                .isInstanceOf(SecurityViolationException.class)
                .satisfies(e -> {
                    SecurityViolationException sve = (SecurityViolationException) e;
                    assertThat(sve.getViolationRule()).isIn("BLOCKED_IMPORT", "RESTRICTED_CLASS_USAGE");
                });
    }

    @Test
    @DisplayName("Should reject imports with whitespace before semicolon")
    void testBlockedImportWithWhitespaceBeforeSemicolon() {
        CodeSecurityValidator validator = new CodeSecurityValidator(SecurityPolicy.strict());
        String code = "package umrao.test;\nimport java.net.Socket   ;\npublic class Tmp {}";

        assertThatThrownBy(() -> validator.validate(code))
                .isInstanceOf(SecurityViolationException.class);
    }

    @Test
    @DisplayName("Should reject fully qualified class name usage in method body without import")
    void testBlockedFqcnWithoutImport() {
        CodeSecurityValidator validator = new CodeSecurityValidator(SecurityPolicy.strict());
        String code = """
                package umrao.test;
                public class UntrustedPayload {
                    public void execute() {
                        java.net.Socket s = null;
                    }
                }
                """;

        assertThatThrownBy(() -> validator.validate(code))
                .isInstanceOf(SecurityViolationException.class)
                .satisfies(e -> {
                    SecurityViolationException sve = (SecurityViolationException) e;
                    assertThat(sve.getViolationRule()).isEqualTo("RESTRICTED_CLASS_USAGE");
                });
    }

    @Test
    @DisplayName("Should reject restricted API calls with whitespace variations")
    void testBlockedPatternWithWhitespace() {
        CodeSecurityValidator validator = new CodeSecurityValidator(SecurityPolicy.strict());
        String code = "package umrao.test;\npublic class Tmp { void f() { Runtime . getRuntime ( ) . exec ( \"whoami\" ); } }";

        assertThatThrownBy(() -> validator.validate(code))
                .isInstanceOf(SecurityViolationException.class)
                .satisfies(e -> {
                    SecurityViolationException sve = (SecurityViolationException) e;
                    assertThat(sve.getViolationRule()).isEqualTo("RESTRICTED_API");
                });
    }

    @ParameterizedTest(name = "Strict policy should block pattern: {0}")
    @ValueSource(strings = {
            "Runtime.getRuntime()",
            "ProcessBuilder",
            "System.exit",
            "System.load",
            "System.loadLibrary",
            "Unsafe"
    })
    @DisplayName("Should reject restricted code tokens and method calls")
    void testBlockedPatterns(String restrictedPattern) {
        CodeSecurityValidator validator = new CodeSecurityValidator(SecurityPolicy.strict());
        String code = "package umrao.test;\npublic class Tmp { void f() { " + restrictedPattern + "; } }";

        assertThatThrownBy(() -> validator.validate(code))
                .isInstanceOf(SecurityViolationException.class)
                .satisfies(e -> {
                    SecurityViolationException sve = (SecurityViolationException) e;
                    assertThat(sve.getViolationRule()).isEqualTo("RESTRICTED_API");
                });
    }

    @Test
    @DisplayName("Should reject code exceeding configured byte length limit")
    void testPayloadSizeLimit() {
        SecurityPolicy tinyPolicy = SecurityPolicy.builder()
                .maxSourceLength(50)
                .build();
        CodeSecurityValidator validator = new CodeSecurityValidator(tinyPolicy);

        String longCode = "public class A { // Very long comment that will certainly exceed the tiny limit }";

        assertThatThrownBy(() -> validator.validate(longCode))
                .isInstanceOf(SecurityViolationException.class)
                .satisfies(e -> {
                    SecurityViolationException sve = (SecurityViolationException) e;
                    assertThat(sve.getViolationRule()).isEqualTo("PAYLOAD_SIZE_LIMIT");
                });
    }

    @Test
    @DisplayName("Should allow compliant code without restrictions")
    void testPermittedCode() {
        CodeSecurityValidator validator = new CodeSecurityValidator(SecurityPolicy.strict());
        String validCode = """
                package umrao.test;
                import java.util.List;
                import java.util.ArrayList;
                
                public class CompliantClass {
                    public List<String> getNames() {
                        return new ArrayList<>();
                    }
                }
                """;

        assertThatCode(() -> validator.validate(validCode)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject compilation attempt via DynamicCompiler if policy is violated")
    void testCompilerIntegration() {
        DynamicCompiler compiler = new InMemoryCompiler(SecurityPolicy.strict(), null, null);
        String exploit = """
                package umrao.generated;
                import umrao.std.Message;
                public class RceMsg implements Message {
                    public void showMessage() {
                        System.exit(1);
                    }
                }
                """;

        assertThatThrownBy(() -> compiler.compile("umrao.generated.RceMsg", exploit, Message.class))
                .isInstanceOf(SecurityViolationException.class)
                .satisfies(e -> {
                    SecurityViolationException sve = (SecurityViolationException) e;
                    assertThat(sve.getViolationRule()).isEqualTo("RESTRICTED_API");
                });
    }
}
