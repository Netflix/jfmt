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

/**
 * Tests for jfmt.
 *
 * @addExports jdk.compiler/com.sun.tools.javac.api=com.netflix.tools.jfmt
 * @addExports jdk.compiler/com.sun.tools.javac.code=com.netflix.tools.jfmt
 * @addExports jdk.compiler/com.sun.tools.javac.file=com.netflix.tools.jfmt
 * @addExports jdk.compiler/com.sun.tools.javac.model=com.netflix.tools.jfmt
 * @addExports jdk.compiler/com.sun.tools.javac.parser=com.netflix.tools.jfmt
 * @addExports jdk.compiler/com.sun.tools.javac.tree=com.netflix.tools.jfmt
 * @addExports jdk.compiler/com.sun.tools.javac.util=com.netflix.tools.jfmt
 */
open module com.netflix.tools.jfmt.test {
    requires com.netflix.tools.jfmt;
    requires java.compiler;
    requires org.junit.jupiter; // @6.1.3

    uses javax.tools.Tool;
    uses java.util.spi.ToolProvider;
}
