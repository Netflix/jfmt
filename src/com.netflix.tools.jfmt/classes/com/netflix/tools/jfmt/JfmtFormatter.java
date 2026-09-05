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
        return formatter.formatSourceAndFixImports(input);
    }

    /** Format specified character ranges of a Java source file. */
    public String formatSource(String input, Collection<Range<Integer>> characterRanges) throws FormatterException {
        return formatter.formatSource(input, characterRanges);
    }
}
