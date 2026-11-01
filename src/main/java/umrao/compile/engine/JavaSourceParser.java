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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust utility to parse and extract package and class information from Java source code.
 * Strips comments, strings, and text blocks before analysis.
 *
 * @author Vivek Umrao
 */
public final class JavaSourceParser {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("\\bpackage\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern PUBLIC_TYPE_PATTERN = Pattern.compile(
            "\\bpublic\\s+(?:(?:final|abstract|sealed|non-sealed|static)\\s+|@[a-zA-Z0-9_.$]+(?:\\([^)]*\\))?\\s+)*(?<!@)\\b(class|interface|record|enum)\\s+([A-Za-z0-9_$]+)"
    );
    private static final Pattern ANY_TYPE_PATTERN = Pattern.compile(
            "(?:(?:public|protected|private|final|abstract|sealed|non-sealed|static)\\s+|@[a-zA-Z0-9_.$]+(?:\\([^)]*\\))?\\s+)*(?<!@)\\b(class|interface|record|enum)\\s+([A-Za-z0-9_$]+)"
    );

    private JavaSourceParser() {
    }

    /**
     * Extracts the fully qualified class name (package + simple name) from Java source code.
     *
     * @param sourceCode raw Java source string
     * @return fully qualified class name
     * @throws IllegalArgumentException if no valid class/interface/record/enum declaration is found
     */
    public static String parseFullClassName(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("Source code must not be null or blank.");
        }

        String cleaned = stripCommentsAndLiterals(sourceCode);
        String packageName = extractPackageName(cleaned);
        String simpleName = extractSimpleClassName(cleaned);

        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    /**
     * Extracts package name from Java source code, or empty string if default package.
     */
    public static String parsePackageName(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return "";
        }
        return extractPackageName(stripCommentsAndLiterals(sourceCode));
    }

    /**
     * Extracts the simple class name (without package) from Java source code.
     */
    public static String parseSimpleClassName(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("Source code must not be null or blank.");
        }
        return extractSimpleClassName(stripCommentsAndLiterals(sourceCode));
    }

    private static String extractPackageName(String cleanedCode) {
        Matcher matcher = PACKAGE_PATTERN.matcher(cleanedCode);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String extractSimpleClassName(String cleanedCode) {
        // 1. Prefer public type declaration if present
        Matcher publicMatcher = PUBLIC_TYPE_PATTERN.matcher(cleanedCode);
        if (publicMatcher.find()) {
            return publicMatcher.group(2).trim();
        }

        // 2. Fall back to any top-level type declaration
        Matcher anyMatcher = ANY_TYPE_PATTERN.matcher(cleanedCode);
        if (anyMatcher.find()) {
            return anyMatcher.group(2).trim();
        }

        throw new IllegalArgumentException(
                "Unable to infer class name from source code. Ensure code declares a class, interface, record, or enum."
        );
    }

    /**
     * Strips single-line comments, block comments, string literals, and text blocks
     * so keywords inside strings/comments are not mistaken for class/package declarations.
     * Preserves whitespace separators so adjacent tokens are not concatenated.
     */
    public static String stripCommentsAndLiterals(String source) {
        StringBuilder sb = new StringBuilder(source.length());
        int len = source.length();
        boolean inBlockComment = false;
        boolean inLineComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean inTextBlock = false;

        for (int i = 0; i < len; i++) {
            char c = source.charAt(i);
            char next = (i + 1 < len) ? source.charAt(i + 1) : '\0';

            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                    sb.append(' '); // Ensure token separation
                }
                continue;
            }
            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                    sb.append(c);
                }
                continue;
            }
            if (inTextBlock) {
                if (c == '"' && next == '"' && i + 2 < len && source.charAt(i + 2) == '"') {
                    inTextBlock = false;
                    i += 2;
                    sb.append(' ');
                }
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    i++; // skip escaped character
                } else if (c == '"') {
                    inString = false;
                    sb.append(' ');
                }
                continue;
            }
            if (inChar) {
                if (c == '\\') {
                    i++;
                } else if (c == '\'') {
                    inChar = false;
                    sb.append(' ');
                }
                continue;
            }

            // Check transitions
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '"' && next == '"' && i + 2 < len && source.charAt(i + 2) == '"') {
                inTextBlock = true;
                i += 2;
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                continue;
            }

            sb.append(c);
        }
        return sb.toString();
    }
}
