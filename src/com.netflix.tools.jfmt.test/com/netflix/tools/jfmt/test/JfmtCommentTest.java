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
    void formatsCommentOnlyBlocksConsistently() {
        for (String comment : new String[] {"/* cleanup */", "/* first */ /* second */", "/* first\n * second */"}) {
            String expected = JfmtTestSupport.format("class Example{void close(){ " + comment + " }}");
            for (String whitespace : new String[] {" ", "\n"}) {
                String input = "class Example{void close(){" + whitespace + comment + "\n}}";
                String formatted = JfmtTestSupport.format(input);

                assertEquals(expected, formatted);
                assertEquals(formatted, JfmtTestSupport.format(formatted));
            }
        }
        String expected =
                """
                class Example {
                    void close() {
                        /* cleanup */
                    }
                }
                """;
        assertEquals(expected, JfmtTestSupport.format("class Example{void close(){ /* cleanup */ }}"));
    }

    @Test
    void formatsCommentOnlyDeclarationsConsistently() {
        for (String input : new String[] {"class Example { /* cleanup */ }", "interface Example { /* cleanup */ }", "record Example() { /* cleanup */ }",
                "class Example { Example() { /* cleanup */ } }", "class Example { static { /* cleanup */ } }"}) {
            String formatted = JfmtTestSupport.format(input);

            assertEquals(formatted, JfmtTestSupport.format(formatted));
            assertEquals(formatted, JfmtTestSupport.format(input.replace("{ /*", "{\n/*")));
        }
    }

    @Test
    void formatsCommentOnlyControlAndLambdaBlocksConsistently() {
        for (String statement : new String[] {"if (true) { /* cleanup */ }", "while (true) { /* cleanup */ }", "synchronized (this) { /* cleanup */ }",
                "Runnable task = () -> { /* cleanup */ };", "try { /* cleanup */ } finally { /* cleanup */ }"}) {
            String formatted = JfmtTestSupport.format("class Example{void run(){" + statement + "}}");

            assertEquals(formatted, JfmtTestSupport.format(formatted));
            assertEquals(formatted, JfmtTestSupport.format("class Example{void run(){" + statement.replace("{ /*", "{\n/*") + "}}"));
        }
    }

    @Test
    void alignsArrayClosingBraceAfterTrailingLineComment() {
        for (String comma : new String[] {"", ","}) {
            String input = "class Example{static final int[] ROWS={\n1,0,0, // row 0\n0,1,0, // row 1\n0,0,1" + comma + " // row 2\n};}";
            String
                    expected = """
                    class Example {
                        static final int[] ROWS = {
                            1, 0, 0, // row 0
                            0, 1, 0, // row 1
                            0, 0, 1%s // row 2
                        };
                    }
                    """
                                    .formatted(comma);

            String formatted = JfmtTestSupport.format(input);

            assertEquals(expected, formatted);
            assertEquals(formatted, JfmtTestSupport.format(formatted));
        }
    }

    @Test
    void preservesSpecialAndOrdinaryLineCommentText() {
        for (String comment : new String[] {"//$NON-NLS-1$", "//$NON-NLS-1$ //$NON-NLS-2$", "//noinspection unused", "//DEPS example:dependency:1", "//ordinary"}) {
            String input = "class Example { String value = \"text\"; " + comment + "\n}";
            String expected = "class Example {\n    String value = \"text\"; " + comment + "\n}\n";

            String formatted = JfmtTestSupport.format(input);

            assertEquals(expected, formatted);
            assertEquals(formatted, JfmtTestSupport.format(formatted));
        }
    }

    @Test
    void preservesStandaloneJbangDirectives() {
        String input = "///usr/bin/env jbang \"$0\" \"$@\" ; exit $?\n//DEPS example:dependency:1\n//JAVA 25+\nclass Example {}\n";

        assertEquals(input, JfmtTestSupport.format(input));
    }

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
    void formatsMarkdownJavadoc() {
        String input =
                """
                class Example {
                /// Returns the configured value after resolving every inherited setting from the surrounding service and its parent configuration.
                ///
                /// - first value
                /// - second value
                Object value(){return null;}
                }
                """;
        String expected =
                """
                class Example {
                    /// Returns the configured value after resolving every inherited setting
                    /// from the surrounding service and its parent configuration.
                    ///
                    /// - first value
                    /// - second value
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
