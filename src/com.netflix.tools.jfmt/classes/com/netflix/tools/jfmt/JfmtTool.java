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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.tools.OptionChecker;
import javax.tools.Tool;

/** Stream-aware jfmt tool service. */
public final class JfmtTool implements Tool, OptionChecker {

    private final JfmtToolProvider provider = new JfmtToolProvider();

    @Override
    public String name() {
        return "jfmt";
    }

    @Override
    public int isSupportedOption(String option) {
        return provider.isSupportedOption(option);
    }

    @Override
    public int run(InputStream in, OutputStream out, OutputStream err,
                   String... args) {
        return provider.run(in, new PrintWriter(out, true, StandardCharsets.UTF_8),
                new PrintWriter(err, true, StandardCharsets.UTF_8), args);
    }

    @Override
    public Set<SourceVersion> getSourceVersions() {
        return EnumSet.allOf(SourceVersion.class);
    }
}
