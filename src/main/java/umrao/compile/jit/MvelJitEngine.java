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

import org.mvel2.MVEL;

import java.io.Serializable;
import java.util.Map;

/**
 * MVEL JIT Expression Engine:
 * Compiles dynamic expressions into optimized bytecode structures for sub-microsecond repeated evaluations.
 *
 * @author Vivek Umrao
 */
public class MvelJitEngine {

    /**
     * Compiles an expression string into a compiled executable bytecode structure.
     */
    public Serializable compile(String expression) {
        return MVEL.compileExpression(expression);
    }

    /**
     * Evaluates a pre-compiled expression against runtime context variables.
     */
    public Object execute(Serializable compiledExpression, Map<String, Object> context) {
        return MVEL.executeExpression(compiledExpression, context);
    }

    /**
     * One-shot compilation and evaluation.
     */
    public Object eval(String expression, Map<String, Object> context) {
        return MVEL.eval(expression, context);
    }
}
