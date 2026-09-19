/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.tools.jfmt;

import java.util.Collection;

import com.netflix.tools.jfmt.internal.com.google.common.collect.Range;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.Formatter;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.FormatterException;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.JavaFormatterOptions;

/**
 * Java source formatter using four-space block and eight-space continuation
 * indentation.
 */
public final class JfmtFormatter {

    private final Formatter formatter;

    public JfmtFormatter() {
        JavaFormatterOptions options = JavaFormatterOptions.builder()
                .formatJavadoc(true)
                .reorderModifiers(true)
                .build();
        this.formatter = new Formatter(options);
    }

    /**
     * Format a Java source file, reorder imports, and remove unused imports.
     */
    public String formatSource(String input) throws FormatterException {
        String formatted = separateAdjacentBlockComments(formatter.formatSourceAndFixImports(input));
        for (int pass = 0; pass < 8; pass++) {
            String next = separateAdjacentBlockComments(formatter.formatSource(formatted));
            if (next.equals(formatted)) {
                return formatted;
            }
            formatted = next;
        }
        throw new FormatterException("Formatting did not converge");
    }

    private static String separateAdjacentBlockComments(String input) {
        var output = new StringBuilder(input.length());
        String lineSeparator = input.contains("\r\n") ? "\r\n" : "\n";
        var state = LexicalState.NORMAL;
        for (int i = 0; i < input.length();) {
            if (state == LexicalState.NORMAL && input.startsWith("\"\"\"", i)) {
                output.append("\"\"\"");
                i += 3;
                state = LexicalState.TEXT_BLOCK;
            } else if (state == LexicalState.NORMAL && input.startsWith("//", i)) {
                output.append("//");
                i += 2;
                state = LexicalState.LINE_COMMENT;
            } else if (state == LexicalState.NORMAL && input.startsWith("/*", i)) {
                output.append("/*");
                i += 2;
                state = LexicalState.BLOCK_COMMENT;
            } else if (state == LexicalState.NORMAL && input.charAt(i) == '"') {
                output.append('"');
                i++;
                state = LexicalState.STRING;
            } else if (state == LexicalState.NORMAL && input.charAt(i) == '\'') {
                output.append('\'');
                i++;
                state = LexicalState.CHARACTER;
            } else if ((state == LexicalState.STRING || state == LexicalState.CHARACTER || state == LexicalState.TEXT_BLOCK)
                    && input.charAt(i) == '\\'
                    && i + 1 < input.length()) {
                output.append(input, i, i + 2);
                i += 2;
            } else if (state == LexicalState.STRING && input.charAt(i) == '"') {
                output.append('"');
                i++;
                state = LexicalState.NORMAL;
            } else if (state == LexicalState.CHARACTER && input.charAt(i) == '\'') {
                output.append('\'');
                i++;
                state = LexicalState.NORMAL;
            } else if (state == LexicalState.TEXT_BLOCK && input.startsWith("\"\"\"", i)) {
                output.append("\"\"\"");
                i += 3;
                state = LexicalState.NORMAL;
            } else if (state == LexicalState.LINE_COMMENT && (input.charAt(i) == '\n' || input.charAt(i) == '\r')) {
                output.append(input.charAt(i++));
                state = LexicalState.NORMAL;
            } else if (state == LexicalState.BLOCK_COMMENT && input.startsWith("*/", i)) {
                output.append("*/");
                i += 2;
                state = LexicalState.NORMAL;
                if (input.startsWith("/*", i)) {
                    output.append(lineSeparator);
                }
            } else {
                output.append(input.charAt(i++));
            }
        }
        return output.toString();
    }

    private enum LexicalState {
        NORMAL,
        STRING,
        CHARACTER,
        TEXT_BLOCK,
        LINE_COMMENT,
        BLOCK_COMMENT
    }

    /** Format specified character ranges of a Java source file. */
    public String formatSource(String input, Collection<Range<Integer>> characterRanges) throws FormatterException {
        return formatter.formatSource(input, characterRanges);
    }
}
