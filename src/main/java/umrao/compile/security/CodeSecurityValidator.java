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
package umrao.compile.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import umrao.compile.engine.JavaSourceParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates untrusted dynamic Java source code against the configured SecurityPolicy
 * before compiler invocation to prevent RCE, injection, and resource exhaustion attacks.
 *
 * @author Vivek Umrao
 */
public class CodeSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(CodeSecurityValidator.class);
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+(?:static\\s+)?([a-zA-Z0-9_.*]+)\\s*;",
            Pattern.MULTILINE
    );

    private final SecurityPolicy policy;

    public CodeSecurityValidator(SecurityPolicy policy) {
        this.policy = policy != null ? policy : SecurityPolicy.strict();
    }

    public void validate(String sourceCode) {
        if (!policy.isEnforceSandbox()) {
            return;
        }

        if (sourceCode == null || sourceCode.isBlank()) {
            throw new SecurityViolationException("EMPTY_SOURCE", "Source code cannot be null or empty.");
        }

        if (sourceCode.length() > policy.getMaxSourceLengthBytes()) {
            log.warn("Security violation: source code size {} exceeds limit {}", sourceCode.length(), policy.getMaxSourceLengthBytes());
            throw new SecurityViolationException("PAYLOAD_SIZE_LIMIT",
                    String.format("Source code size (%d chars) exceeds maximum permitted limit (%d).",
                            sourceCode.length(), policy.getMaxSourceLengthBytes()));
        }

        // 1. Check blocked imports
        Matcher matcher = IMPORT_PATTERN.matcher(sourceCode);
        while (matcher.find()) {
            String importedPackage = matcher.group(1).trim();
            for (String blocked : policy.getBlockedImports()) {
                if (importedPackage.equals(blocked) || importedPackage.startsWith(blocked + ".")) {
                    log.warn("Security violation: blocked package import detected: {}", importedPackage);
                    throw new SecurityViolationException("BLOCKED_IMPORT",
                            "Importing restricted package or class: " + importedPackage);
                }
            }
        }

        // 2. Strip comments and literals to prevent obfuscation and avoid false positives in strings
        String strippedCode = JavaSourceParser.stripCommentsAndLiterals(sourceCode);

        // 3. Prevent Fully Qualified Class Name (FQCN) bypasses in method bodies (e.g. new java.net.Socket(...))
        for (String blocked : policy.getBlockedImports()) {
            Pattern fqcnPattern = Pattern.compile("\\b" + Pattern.quote(blocked) + "(?:\\b|\\.)");
            if (fqcnPattern.matcher(strippedCode).find()) {
                log.warn("Security violation: restricted package/class usage detected in code body: {}", blocked);
                throw new SecurityViolationException("RESTRICTED_CLASS_USAGE",
                        "Source code references forbidden class or package: " + blocked);
            }
        }

        // 4. Check blocked patterns / forbidden APIs in source code (allowing flexible whitespace)
        for (String pattern : policy.getBlockedPatterns()) {
            if (pattern.contains(".")) {
                String[] parts = pattern.split("\\.");
                StringBuilder regex = new StringBuilder("\\b");
                for (int i = 0; i < parts.length; i++) {
                    regex.append(Pattern.quote(parts[i]));
                    if (i < parts.length - 1) {
                        regex.append("\\s*\\.\\s*");
                    }
                }
                regex.append("\\b");
                Pattern flexiblePattern = Pattern.compile(regex.toString());
                if (flexiblePattern.matcher(sourceCode).find() || flexiblePattern.matcher(strippedCode).find()) {
                    log.warn("Security violation: restricted API token detected: {}", pattern);
                    throw new SecurityViolationException("RESTRICTED_API",
                            "Source code contains forbidden API or token: " + pattern);
                }
            } else {
                Pattern tokenPattern = Pattern.compile("\\b" + Pattern.quote(pattern) + "\\b");
                if (tokenPattern.matcher(sourceCode).find() || tokenPattern.matcher(strippedCode).find()) {
                    log.warn("Security violation: restricted API token detected: {}", pattern);
                    throw new SecurityViolationException("RESTRICTED_API",
                            "Source code contains forbidden API or token: " + pattern);
                }
            }
        }
    }
}
