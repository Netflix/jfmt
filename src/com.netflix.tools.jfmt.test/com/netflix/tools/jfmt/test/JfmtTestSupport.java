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

package com.netflix.tools.jfmt.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import javax.tools.Tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class JfmtTestSupport {

    private JfmtTestSupport() {}

    static String format(String input) {
        Tool tool = ServiceLoader.load(Tool.class).stream()
                .map(Provider::get)
                .filter(candidate -> candidate.name().equals("jfmt"))
                .findFirst()
                .orElseThrow();
        var output = new ByteArrayOutputStream();
        var error = new ByteArrayOutputStream();
        int exitCode = tool.run(new ByteArrayInputStream(input.getBytes(UTF_8)), output, error, "-");
        assertEquals(0, exitCode, error.toString(UTF_8));
        return output.toString(UTF_8);
    }
}
