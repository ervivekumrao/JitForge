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
import umrao.compile.engine.InMemoryCompiler;
import umrao.std.Log;
import umrao.std.Print;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LegacyApiTest {

    @Test
    @DisplayName("Should execute OnTheFlyCompiler with custom InMemoryCompiler instance")
    void testOnTheFlyCompilerWithCustomEngine() {
        OnTheFlyCompiler compiler = new OnTheFlyCompiler(new InMemoryCompiler());
        assertThatCode(compiler::compile).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should verify Log and Print legacy utility classes")
    void testLegacyStdUtilities() {
        Log log = new Log();
        log.printLog();
        assertThat(log.getLogger()).isNotNull();

        Print print = new Print();
        assertThatCode(print::printMessage).doesNotThrowAnyException();
    }
}
