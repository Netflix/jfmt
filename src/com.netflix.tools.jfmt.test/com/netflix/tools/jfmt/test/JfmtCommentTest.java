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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JfmtCommentTest {

    @Test
    void wrapsLongJavadocAtEightyColumns() {
        String input =
                """
                class Example {
                    /** Returns the configured value after resolving every inherited setting from the surrounding service and its parent configuration. */
                    Object value(){return null;}
                }
                """;
        String expected =
                """
                class Example {
                    /**
                     * Returns the configured value after resolving every inherited setting from
                     * the surrounding service and its parent configuration.
                     */
                    Object value() {
                        return null;
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void doesNotBreakAnInlineTagThatCrossesTheJavadocWidth() {
        String input =
                """
                class Example {
                    /**
                     * Returns the value from {@link com.example.configuration.InheritedConfigurationResolver#resolveValue()}.
                     */
                    Object value(){return null;}
                }
                """;
        String expected =
                """
                class Example {
                    /**
                     * Returns the value from {@link com.example.configuration.InheritedConfigurationResolver#resolveValue()}.
                     */
                    Object value() {
                        return null;
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void preservesPreformattedJavadocContent() {
        String input =
                """
                class Example {
                    /**
                     * {@snippet :
                     * String value = service.resolve(firstArgument, secondArgument, thirdArgument, fourthArgument);
                     * }
                     */
                    Object value(){return null;}
                }
                """;
        String expected =
                """
                class Example {
                    /**
                     * {@snippet :
                     * String value = service.resolve(firstArgument, secondArgument, thirdArgument, fourthArgument);
                     * }
                     */
                    Object value() {
                        return null;
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void doesNotReflowLineComments() {
        String input =
                """
                class Example {
                    void run(){
                        // This explanation is intentionally kept together because ordinary comments retain the line breaks chosen by the author.
                        work();
                    }
                }
                """;
        String expected =
                """
                class Example {
                    void run() {
                        // This explanation is intentionally kept together because ordinary comments retain the line breaks chosen by the author.
                        work();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void doesNotReflowBlockComments() {
        String input =
                """
                class Example {
                    void run(){
                        /* This explanation is intentionally kept together because ordinary comments retain the line breaks chosen by the author. */
                        work();
                    }
                }
                """;
        String expected =
                """
                class Example {
                    void run() {
                        /* This explanation is intentionally kept together because ordinary comments retain the line breaks chosen by the author. */
                        work();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void preservesWellWrappedJavadoc() {
        String input =
                """
                class Example {
                    /**
                     * Returns the configured value.
                     *
                     * <p>The value is resolved lazily so callers do not need to initialize the
                     * surrounding service eagerly.
                     */
                    Object value(){return null;}
                }
                """;
        String expected =
                """
                class Example {
                    /**
                     * Returns the configured value.
                     *
                     * <p>The value is resolved lazily so callers do not need to initialize the
                     * surrounding service eagerly.
                     */
                    Object value() {
                        return null;
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }
}
