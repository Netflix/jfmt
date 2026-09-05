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

class JfmtStyleTest {

    @Test
    void placesStaticImportsAfterOrdinaryImports() {
        String input = "import static java.util.Collections.emptyList;import sun.misc.Unsafe;import java.util.List;import jdk.internal.misc.VM;class Example{List<?> values=emptyList();Unsafe unsafe;Class<?> vm=VM.class;}";
        String expected =
                """
                import java.util.List;

                import jdk.internal.misc.VM;
                import sun.misc.Unsafe;

                import static java.util.Collections.emptyList;

                class Example {
                    List<?> values = emptyList();
                    Unsafe unsafe;
                    Class<?> vm = VM.class;
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void ordersJdkImportGroups() {
        String input = "import static java.util.Collections.emptyList;import com.example.Widget;import javax.tools.Tool;import java.util.List;import module java.base;class Example{List<Widget> values=emptyList();Tool tool;}";
        String expected =
                """
                import module java.base;

                import java.util.List;
                import javax.tools.Tool;

                import com.example.Widget;

                import static java.util.Collections.emptyList;

                class Example {
                    List<Widget> values = emptyList();
                    Tool tool;
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void preservesCommentedImportGroups() {
        String input =
                """
                package example;

                // java imports
                import java.util.Set;
                import java.io.ObjectInputStream;

                // project imports
                import example.loading.Loader;

                class Example {
                    Set<String> values;
                    ObjectInputStream input;
                    Loader loader;
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(input, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void hasNoCodeLineLimit() {
        String methodName = "method" + "x".repeat(1_100);
        String input = "class NoLineLimit { void test() { " + methodName + "(a, b, c); } }\n";
        String
                expected = """
                class NoLineLimit {
                    void test() {
                        %s(a, b, c);
                    }
                }
                """
                                .formatted(methodName);

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void placesFieldAnnotationsBeforeTheDeclaration() {
        String input = "class Example{@Stable static Object value;}";
        String expected =
                """
                class Example {
                    @Stable
                    static Object value;
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void indentsSwitchExpressionBlockAndYield() {
        String input = "class Example{Calendar create(String type){return switch(type){case \"gregory\"->new GregorianCalendar();case \"iso8601\"->{GregorianCalendar calendar=new GregorianCalendar();calendar.setGregorianChange(new Date(Long.MIN_VALUE));yield calendar;}default->throw new IllegalArgumentException(type);};}}";
        String expected =
                """
                class Example {
                    Calendar create(String type) {
                        return switch (type) {
                            case "gregory" -> new GregorianCalendar();
                            case "iso8601" -> {
                                GregorianCalendar calendar = new GregorianCalendar();
                                calendar.setGregorianChange(new Date(Long.MIN_VALUE));
                                yield calendar;
                            }
                            default -> throw new IllegalArgumentException(type);
                        };
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void continuesWrappedSwitchRuleExpression() {
        String input = "class Example{Format create(Kind kind,Locale locale){return switch(kind){case DEFAULT->DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).toFormat();case SHORT->DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).toFormat();default->throw new IllegalArgumentException(\"Unsupported format kind: \"+kind);};}}";
        String expected =
                """
                class Example {
                    Format create(Kind kind, Locale locale) {
                        return switch (kind) {
                            case DEFAULT ->
                                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                                            .withLocale(locale)
                                            .toFormat();
                            case SHORT ->
                                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                                            .withLocale(locale)
                                            .toFormat();
                            default -> throw new IllegalArgumentException("Unsupported format kind: " + kind);
                        };
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void continuesGuardedSwitchRuleBody() {
        String input = "class Example{void transform(Object element){switch(element){case ClassFileVersion version when version.majorVersion()>ClassFile.JAVA_22_VERSION->throw new IllegalArgumentException(\"Cannot transform class file version \"+version.majorVersion());case ClassFileVersion version->transform(version);default->ignore(element);}}}";
        String expected =
                """
                class Example {
                    void transform(Object element) {
                        switch (element) {
                            case ClassFileVersion version when version.majorVersion() > ClassFile.JAVA_22_VERSION ->
                                    throw new IllegalArgumentException("Cannot transform class file version " + version.majorVersion());
                            case ClassFileVersion version -> transform(version);
                            default -> ignore(element);
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void indentsTraditionalSwitchStatementGroups() {
        String input = "class Example{void handle(State state){switch(state){case STARTED:start();break;case STOPPED:stop();break;default:throw new IllegalStateException();}}}";
        String expected =
                """
                class Example {
                    void handle(State state) {
                        switch (state) {
                            case STARTED:
                                start();
                                break;
                            case STOPPED:
                                stop();
                                break;
                            default:
                                throw new IllegalStateException();
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void indentsSwitchExpressionRules() {
        String input = "class Example{String describe(State state){return switch(state){case UNAVAILABLE->\"Unavailable\";case SUCCESS->\"Completed successfully\";case FAILED->\"Failed\";default->\"Unknown\";};}}";
        String expected =
                """
                class Example {
                    String describe(State state) {
                        return switch (state) {
                            case UNAVAILABLE -> "Unavailable";
                            case SUCCESS -> "Completed successfully";
                            case FAILED -> "Failed";
                            default -> "Unknown";
                        };
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void alignsRecordComponentsWithFirstComponent() {
        String input = "class E{record Pair(String first,String second,String third,String fourth){}}";
        String expected =
                """
                class E {
                    record Pair(String first, String second, String third,
                                String fourth) {}
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void usesContinuationIndentForDeepRecordComponentAlignment() {
        String input = "class Example{private record PackagingTaskContext(BuildEnvironment environment,PackageDefinition packageDefinition,Path outputDirectory,ApplicationImageLayout sourceApplicationImage,Optional<Configuration> configuration)implements TaskContext,StartupParameters{}}";
        String expected =
                """
                class Example {
                    private record PackagingTaskContext(BuildEnvironment environment, PackageDefinition packageDefinition, Path outputDirectory,
                            ApplicationImageLayout sourceApplicationImage, Optional<Configuration> configuration)
                            implements TaskContext, StartupParameters {}
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksAfterSixPermittedTypes() {
        String input = "public sealed interface MemoryLayout permits SequenceLayout,GroupLayout,PaddingLayout,ValueLayout,AddressLayout,StructLayout,UnionLayout{}";
        String expected =
                """
                public sealed interface MemoryLayout
                        permits SequenceLayout, GroupLayout, PaddingLayout, ValueLayout, AddressLayout, StructLayout,
                                UnionLayout {}
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksDensePermitsClause() {
        String input = "sealed interface E permits A,B,C,D,F,G,H{}";
        String expected =
                """
                sealed interface E
                        permits A, B, C, D, F, G,
                                H {}
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsSixPermittedTypesTogether() {
        String input = "sealed interface E permits A,B,C,D,F,G{}";
        String expected =
                """
                sealed interface E permits A, B, C, D, F, G {}
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void composesSealedTypeHeaderClauses() {
        String input = "abstract sealed class AbstractSpecies<ElementType>extends VectorSupport.VectorSpecies<ElementType>implements VectorSpecies<ElementType>,Serializable,Constable permits ByteVector.ByteSpecies,DoubleVector.DoubleSpecies,FloatVector.FloatSpecies,IntVector.IntSpecies,LongVector.LongSpecies,ShortVector.ShortSpecies{}";
        String expected =
                """
                abstract sealed class AbstractSpecies<ElementType> extends VectorSupport.VectorSpecies<ElementType>
                        implements VectorSpecies<ElementType>, Serializable, Constable
                        permits ByteVector.ByteSpecies, DoubleVector.DoubleSpecies, FloatVector.FloatSpecies, IntVector.IntSpecies, LongVector.LongSpecies, ShortVector.ShortSpecies {}
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void continuesWrappedConditionContainingBindingPattern() {
        String input = "class Example{void inspect(Type type){if(type.getRawType() instanceof Class<?> candidate&&Modifier.isStatic(candidate.getModifiers())&&candidate.getEnclosingClass()!=null&&candidate.isVisible()){use(candidate);}}}";
        String expected =
                """
                class Example {
                    void inspect(Type type) {
                        if (type.getRawType() instanceof Class<?> candidate
                                && Modifier.isStatic(candidate.getModifiers())
                                && candidate.getEnclosingClass() != null
                                && candidate.isVisible()) {
                            use(candidate);
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void alignsRecordPatternComponentsWithFirstComponent() {
        String input = "class E{void f(Object o){if(o instanceof P(A a,B b,C c,D d)){use(a);}}}";
        String expected =
                """
                class E {
                    void f(Object o) {
                        if (o instanceof P(A a, B b, C c,
                                           D d)) {
                            use(a);
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void composesNestedRecordPatternsWithinCondition() {
        String input = "class Example{void inspect(Object value){if(value instanceof Entry(Key(String namespace,String name),Value(String content,long timestamp),Metadata(String owner,String source))&&isCurrent(timestamp)&&isVisible(owner)){use(content);}}}";
        String expected =
                """
                class Example {
                    void inspect(Object value) {
                        if (value instanceof Entry(Key(String namespace, String name), Value(String content, long timestamp),
                                Metadata(String owner, String source))
                                && isCurrent(timestamp) && isVisible(owner)) {
                            use(content);
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void usesContinuationIndentForDeepRecordPatternComponents() {
        String input = "class Example{void render(ScopeToken st){if(!(st instanceof ScopeTokenImpl(List<Token> tokens,boolean isTransparentForNames,boolean isTransparentForHashtags,boolean isTransparentForSetFuelCost))){fail();}}}";
        String expected =
                """
                class Example {
                    void render(ScopeToken st) {
                        if (!(st instanceof ScopeTokenImpl(List<Token> tokens, boolean isTransparentForNames, boolean isTransparentForHashtags,
                                boolean isTransparentForSetFuelCost))) {
                            fail();
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void alignsTryResourcesWithFirstDeclaration() {
        String input = "class Example{void copy(Path source,Path target)throws IOException{try(InputStream input=Files.newInputStream(source);OutputStream output=Files.newOutputStream(target);BufferedInputStream bufferedInput=new BufferedInputStream(input)){bufferedInput.transferTo(output);}}}";
        String expected =
                """
                class Example {
                    void copy(Path source, Path target) throws IOException {
                        try (InputStream input = Files.newInputStream(source);
                             OutputStream output = Files.newOutputStream(target);
                             BufferedInputStream bufferedInput = new BufferedInputStream(input)) {
                            bufferedInput.transferTo(output);
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void alignsExistingAndDeclaredTryResources() {
        String input = "class Example{void read(InputStream input,Path files)throws IOException{try(input;Scanner scanner=new Scanner(Files.newInputStream(files))){scanner.forEachRemaining(this::accept);}}}";
        String expected =
                """
                class Example {
                    void read(InputStream input, Path files) throws IOException {
                        try (input;
                             Scanner scanner = new Scanner(Files.newInputStream(files))) {
                            scanner.forEachRemaining(this::accept);
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksAndAlignsSevenThrownTypes() {
        String input = "abstract class Example{public abstract void verify(PublicKey key)throws A,B,C,D,E1,F,G;}";
        String expected =
                """
                abstract class Example {
                    public abstract void verify(PublicKey key)
                            throws A, B, C, D, E1, F,
                                   G;
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsSixThrownTypesTogether() {
        String input = "abstract class E{abstract void f(Key key)throws A,B,C,D,E1,F;}";
        String expected =
                """
                abstract class E {
                    abstract void f(Key key) throws A, B, C, D, E1, F;
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsThrowsAtItsOwnContinuationAfterWrappedParameters() {
        String input = "class Example{int doFinal(byte[] input,int inputOffset,int inputLength,byte[] output,int outputOffset,AlgorithmParameterSpec parameters)throws IllegalBlockSizeException,ShortBufferException,BadPaddingException{return 0;}}";
        String expected =
                """
                class Example {
                    int doFinal(byte[] input, int inputOffset, int inputLength,
                                byte[] output, int outputOffset, AlgorithmParameterSpec parameters)
                            throws IllegalBlockSizeException, ShortBufferException, BadPaddingException {
                        return 0;
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void continuesWrappedConstructorDelegationArguments() {
        String input = "class ImageHeader{ImageHeader(int resourceCount,int tableCount,int locationsSize,int stringsSize){this(MAGIC,MAJOR_VERSION,MINOR_VERSION,0,resourceCount,tableCount,locationsSize,stringsSize);}}";
        String expected =
                """
                class ImageHeader {
                    ImageHeader(int resourceCount, int tableCount, int locationsSize,
                                int stringsSize) {
                        this(MAGIC, MAJOR_VERSION, MINOR_VERSION, 0, resourceCount, tableCount,
                                locationsSize, stringsSize);
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksAfterSixIntersectionBounds() {
        String input = "class E<T extends A&B&C&D&E1&F&G>{}";
        String expected =
                """
                class E<T extends A & B & C & D & E1 & F
                        & G> {}
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsSixIntersectionBoundsTogether() {
        String input = "class E<T extends A&B&C&D&E1&F>{}";
        String expected =
                """
                class E<T extends A & B & C & D & E1 & F> {}
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksAfterSixMultiCatchAlternatives() {
        String input = "class E{void f(){try{work();}catch(A|B|C|D|E1|F|G exception){recover(exception);}}}";
        String expected =
                """
                class E {
                    void f() {
                        try {
                            work();
                        } catch (A | B | C | D | E1 | F
                                | G exception) {
                            recover(exception);
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsSixMultiCatchAlternativesTogether() {
        String input = "class E{void f(){try{work();}catch(A|B|C|D|E1|F exception){recover(exception);}}}";
        String expected =
                """
                class E {
                    void f() {
                        try {
                            work();
                        } catch (A | B | C | D | E1 | F exception) {
                            recover(exception);
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksAfterSixArrayInitializerElements() {
        String input = "class E{String[] values={\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\"};}";
        String expected =
                """
                class E {
                    String[] values = {"a", "b", "c", "d", "e", "f",
                            "g"};
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsSixArrayInitializerElementsTogether() {
        String input = "class E{String[] values={\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"};}";
        String expected =
                """
                class E {
                    String[] values = {"a", "b", "c", "d", "e", "f"};
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void usesFourSpaceBlockIndentation() {
        String input = "class Example{void run(){if(true){work();}}}";
        String expected =
                """
                class Example {
                    void run() {
                        if (true) {
                            work();
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void alignsMethodParametersWithTheFirstParameter() {
        String input = "class Example{static void someMethod(int anArgument,Object anotherArgument,String yetAnotherArgument,Object andStillAnotherArgument){work();}}";
        String expected =
                """
                class Example {
                    static void someMethod(int anArgument, Object anotherArgument, String yetAnotherArgument,
                                           Object andStillAnotherArgument) {
                        work();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksParametersAfterDenseGenericHeader() {
        String input = "class E{static <A,B> G<A,B> f(F<A,B> x,H<A,I<B>> y){}}";
        String expected =
                """
                class E {
                    static <A, B> G<A, B> f(F<A, B> x,
                                            H<A, I<B>> y) {}
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void usesContinuationIndentForDenseGenericHeaderWhenAlignmentIsTooDeep() {
        String input = "class E{static <A,B> G<A,B> extraordinarilyLongMethodName(F<A,B> x,H<A,I<B>> y){}}";
        String expected =
                """
                class E {
                    static <A, B> G<A, B> extraordinarilyLongMethodName(F<A, B> x,
                            H<A, I<B>> y) {}
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsSmallerGenericHeaderTogether() {
        String input = "class E{static <T> G<T> f(G<T> x,G<T> y){}}";
        String expected =
                """
                class E {
                    static <T> G<T> f(G<T> x, G<T> y) {}
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksAfterThreeSimpleParameters() {
        String input = "class Example{void invoke(int a,int b,int c,int d){}}";
        String expected =
                """
                class Example {
                    void invoke(int a, int b, int c,
                                int d) {}
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsThreeSimpleParametersTogether() {
        String input = "class Example{void invoke(int a,int b,int c){}}";
        String expected =
                """
                class Example {
                    void invoke(int a, int b, int c) {}
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void usesContinuationIndentForDeepMethodParameterAlignment() {
        String input = "class Example{private static synchronized Object horkingLongMethodName(int anArg,Object anotherArg,String yetAnotherArg,Object andStillAnother){return null;}}";
        String expected =
                """
                class Example {
                    private static synchronized Object horkingLongMethodName(int anArg, Object anotherArg, String yetAnotherArg,
                            Object andStillAnother) {
                        return null;
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void addsBracesToUnbracedControlBodies() {
        String input = "class Example{void run(){if(ready)work();if(invalid)throw new IllegalStateException();for(String value:values)use(value);for(int i=0;i<size;i++)use(i);while(ready)work();do work();while(ready);}}";
        String expected =
                """
                class Example {
                    void run() {
                        if (ready) {
                            work();
                        }
                        if (invalid) {
                            throw new IllegalStateException();
                        }
                        for (String value : values) {
                            use(value);
                        }
                        for (int i = 0; i < size; i++) {
                            use(i);
                        }
                        while (ready) {
                            work();
                        }
                        do {
                            work();
                        } while (ready);
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void addsBracesToEarlyReturns() {
        String input = "class Example{Object find(){if(value==null)\nreturn null;return value;}}";
        String expected =
                """
                class Example {
                    Object find() {
                        if (value == null) {
                            return null;
                        }
                        return value;
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void addsBracesToReturnsInIfElseStatements() {
        String input = "class Example{Object select(){if(ready)return left;else return right;}}";
        String expected =
                """
                class Example {
                    Object select() {
                        if (ready) {
                            return left;
                        } else {
                            return right;
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void addsBracesToStructurallyBrokenEarlyReturns() {
        String input = "class Example{Object select(){if(state==A||state==B||state==C||state==D)return result;if(ready)return create(one,two,three,four,five,six,seven);return fallback;}}";
        String expected =
                """
                class Example {
                    Object select() {
                        if (state == A
                                || state == B
                                || state == C
                                || state == D) {
                            return result;
                        }
                        if (ready) {
                            return create(one, two, three, four, five, six,
                                    seven);
                        }
                        return fallback;
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void addsBracesWithoutMovingControlBodyComments() {
        String input =
                """
                class Example {
                    void run() {
                        if (ready) // Explain the return.
                            return;
                        if (finished) return; // Keep this with the return.
                        if (blocked)
                            /* Keep this before the return. */
                            return;
                    }
                }
                """;
        String expected =
                """
                class Example {
                    void run() {
                        if (ready) { // Explain the return.
                            return;
                        }
                        if (finished) {
                            return; // Keep this with the return.
                        }
                        if (blocked) {
                            /* Keep this before the return. */
                            return;
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void addsBracesWithoutChangingDanglingElseAssociation() {
        String input = "class Example{void run(){if(first)if(second)work();else other();}}";
        String expected =
                """
                class Example {
                    void run() {
                        if (first) {
                            if (second) {
                                work();
                            } else {
                                other();
                            }
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksTraversalForLoopClauses() {
        String input = "class Example{void run(){for(Node node=head;node!=null;node=node.next()){use(node);}}}";
        String expected =
                """
                class Example {
                    void run() {
                        for (Node node = head;
                             node != null;
                             node = node.next()) {
                            use(node);
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsSimpleCounterForLoopTogether() {
        String input = "class Example{void run(){for(int i=0;i<size;i++){use(i);}}}";
        String expected =
                """
                class Example {
                    void run() {
                        for (int i = 0; i < size; i++) {
                            use(i);
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void alignsWrappedForLoopClauses() {
        String input = "class Example{void run(){for(Snapshot snapshot=current;containsAll(snapshot.bits(),requiredBits);snapshot=snapshot.previous()){use(snapshot);}}}";
        String expected =
                """
                class Example {
                    void run() {
                        for (Snapshot snapshot = current;
                             containsAll(snapshot.bits(), requiredBits);
                             snapshot = snapshot.previous()) {
                            use(snapshot);
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksFourLogicalComparisons() {
        String input = "class Example{void run(){if(state==A||state==B||state==C||state==D){work();}}}";
        String expected =
                """
                class Example {
                    void run() {
                        if (state == A
                                || state == B
                                || state == C
                                || state == D) {
                            work();
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksLogicalExpressionThatDerivesAnOperand() {
        String input = "class Example{void run(){while(go&&(x=q.peek())!=null&&(x.done()||n>0)){work();}}}";
        String expected =
                """
                class Example {
                    void run() {
                        while (go
                                && (x = q.peek()) != null
                                && (x.done() || n > 0)) {
                            work();
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsThreeLogicalComparisonsTogether() {
        String input = "class Example{void run(){if(state==A||state==B||state==C){work();}}}";
        String expected =
                """
                class Example {
                    void run() {
                        if (state == A || state == B || state == C) {
                            work();
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksBetweenComposedLogicalGroups() {
        String input = "class E{boolean f(){return (run()||a||(b=false))&&(c||d);}}";
        String expected =
                """
                class E {
                    boolean f() {
                        return (run() || a || (b = false))
                                && (c || d);
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsTwoSimpleLogicalGroupsTogether() {
        String input = "class E{boolean f(){return (a||b)&&(c||d);}}";
        String expected =
                """
                class E {
                    boolean f() {
                        return (a || b) && (c || d);
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void usesContinuationIndentForWrappedIfConditions() {
        String input = "class Example{void run(){if((firstCondition&&secondCondition)||(thirdCondition&&fourthCondition)||!(fifthCondition&&sixthCondition)||seventhCondition){work();}}}";
        String expected =
                """
                class Example {
                    void run() {
                        if ((firstCondition && secondCondition)
                                || (thirdCondition && fourthCondition)
                                || !(fifthCondition && sixthCondition)
                                || seventhCondition) {
                            work();
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksConditionalExpressionWithComposedCondition() {
        String input = "class Example{void run(){result=ready&&enabled?create():fallback();}}";
        String expected =
                """
                class Example {
                    void run() {
                        result = ready && enabled
                                ? create()
                                : fallback();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksConditionalExpressionWithComparedAlternatives() {
        String input = "class Example{void run(){result=left==FIRST||right==SECOND?create():DEFAULT.value;}}";
        String expected =
                """
                class Example {
                    void run() {
                        result = left == FIRST || right == SECOND
                                ? create()
                                : DEFAULT.value;
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsConditionalExpressionWithSingleComparisonTogether() {
        String input = "class Example{void run(){result=next!=null?next.get():create();}}";
        String expected =
                """
                class Example {
                    void run() {
                        result = next != null ? next.get() : create();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsSimpleConditionalExpressionTogether() {
        String input = "class Example{void run(){result=ready?create():fallback();}}";
        String expected =
                """
                class Example {
                    void run() {
                        result = ready ? create() : fallback();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsAssignmentWithWrappedConditionalExpression() {
        String input = "class Example{void run(){alpha=(firstCondition&&secondCondition&&thirdCondition&&fourthCondition)?firstResult:secondResult;}}";
        String expected =
                """
                class Example {
                    void run() {
                        alpha = (firstCondition && secondCondition && thirdCondition && fourthCondition)
                                ? firstResult
                                : secondResult;
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksMethodCallAfterCommaWithContinuationIndent() {
        String input = "class Example{void run(){someMethod(longExpression1,longExpression2,longExpression3,longExpression4,longExpression5,longExpression6,longExpression7);}}";
        String expected =
                """
                class Example {
                    void run() {
                        someMethod(longExpression1, longExpression2, longExpression3, longExpression4, longExpression5, longExpression6,
                                longExpression7);
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksDenseCallAfterCommaWithContinuationIndent() {
        String input = "class Example{void run(){ApplicationTarget target=ApplicationTarget.resolve(\"com.example.foo@1.2.3\",ToolServices.of(),new ByteArrayInputStream(new byte[0]),new PrintStream(new ByteArrayOutputStream()));}}";
        String expected =
                """
                class Example {
                    void run() {
                        ApplicationTarget target = ApplicationTarget.resolve("com.example.foo@1.2.3", ToolServices.of(), new ByteArrayInputStream(new byte[0]),
                                new PrintStream(new ByteArrayOutputStream()));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void doesNotTreatPercentEncodedLiteralAsFormatString() {
        String input = "class Example{void run(){ApplicationTarget target=ApplicationTarget.resolve(\"pkg:maven/com.example/example-foo@1.2.3?repository_url=https%3A%2F%2Frepo.example\",ToolServices.of(jig),new ByteArrayInputStream(new byte[0]),new PrintStream(errors,true,StandardCharsets.UTF_8));}}";
        String expected =
                """
                class Example {
                    void run() {
                        ApplicationTarget target = ApplicationTarget.resolve("pkg:maven/com.example/example-foo@1.2.3?repository_url=https%3A%2F%2Frepo.example",
                                ToolServices.of(jig), new ByteArrayInputStream(new byte[0]), new PrintStream(errors, true, StandardCharsets.UTF_8));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void countsNestedCallStructureInArguments() {
        String input = "class Example{void run(){assertEquals(List.of(List.of(\"--lookup-module\",packageUrl),List.of(\"--list-module-versions\",\"com.example.foo\")),requests);}}";
        String expected =
                """
                class Example {
                    void run() {
                        assertEquals(List.of(List.of("--lookup-module", packageUrl), List.of("--list-module-versions", "com.example.foo")),
                                requests);
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void countsNestedCallArgumentsWithinExpressionLambda() {
        String input = "class Example{void run(){IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->CommandAvailability.require(commandLine,ToolServices.of(),new ToolCatalog(List.of())));}}";
        String expected =
                """
                class Example {
                    void run() {
                        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                                () -> CommandAvailability.require(commandLine, ToolServices.of(), new ToolCatalog(List.of())));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void usesBrokenListWithoutBreakingNestedChain() {
        String input = "class Example{CommandLine create(){return new CommandLine(Path.of(\"\").toAbsolutePath(),command,Optional.empty(),Optional.empty(),List.of(),List.of(),List.of());}}";
        String expected =
                """
                class Example {
                    CommandLine create() {
                        return new CommandLine(
                                Path.of("").toAbsolutePath(),
                                command,
                                Optional.empty(),
                                Optional.empty(),
                                List.of(),
                                List.of(),
                                List.of());
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void usesBrokenListInsteadOfWrappingArgumentsTwice() {
        String input = "class Example{void run(){ToolDefinition formatter=new ToolDefinition(\"alternative-formatter\",\"fmt\",Optional.empty(),Optional.of(\"com.example.formatter\"),\"alternative-formatter\",Optional.of(\"1\"),Set.of(\"module-source-path\"),List.of());}}";
        String expected =
                """
                class Example {
                    void run() {
                        ToolDefinition formatter = new ToolDefinition(
                                "alternative-formatter",
                                "fmt",
                                Optional.empty(),
                                Optional.of("com.example.formatter"),
                                "alternative-formatter",
                                Optional.of("1"),
                                Set.of("module-source-path"),
                                List.of());
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void propagatesBrokenArrayInitializerLayoutToEnclosingCall() {
        String input = "class Example{void run(){var commandLine=CommandLine.parse(temporaryDirectory,new String[]{\"init\",\"--release=25\",\"--main-class\",\"com.example.application.Main\",\"--enable-preview\",\"--enable-native-access\",\"org.example.nativebinding,org.example.other\",\"--enable-final-field-mutation=org.example.model\",\"--add-opens\",\"java.base/java.lang=com.example.application,org.example.friend\",\"--add-exports=jdk.compiler/com.sun.tools.javac.tree=com.example.application\",\"com.example.application\"});}}";
        String expected =
                """
                class Example {
                    void run() {
                        var commandLine = CommandLine.parse(
                                temporaryDirectory,
                                new String[] {
                                    "init",
                                    "--release=25",
                                    "--main-class",
                                    "com.example.application.Main",
                                    "--enable-preview",
                                    "--enable-native-access",
                                    "org.example.nativebinding,org.example.other",
                                    "--enable-final-field-mutation=org.example.model",
                                    "--add-opens",
                                    "java.base/java.lang=com.example.application,org.example.friend",
                                    "--add-exports=jdk.compiler/com.sun.tools.javac.tree=com.example.application",
                                    "com.example.application"
                                });
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void propagatesBrokenNestedArgumentLayouts() {
        String input = "class Example{void run(){assertEquals(new Command.Init(new InitRequest(\"com.example.application\",Optional.of(25),Optional.of(\"com.example.application.Main\"),true,List.of(new RequireRequest.RuntimeAccess(\"enableNativeAccess\",\"org.example.nativebinding\"),new RequireRequest.RuntimeAccess(\"enableNativeAccess\",\"org.example.other\"),new RequireRequest.RuntimeAccess(\"enableFinalFieldMutation\",\"org.example.model\"),new RequireRequest.RuntimeAccess(\"addExports\",\"jdk.compiler/com.sun.tools.javac.tree=com.example.application\"),new RequireRequest.RuntimeAccess(\"addOpens\",\"java.base/java.lang=com.example.application\"),new RequireRequest.RuntimeAccess(\"addOpens\",\"java.base/java.lang=org.example.friend\")))),commandLine.command());}}";
        String expected =
                """
                class Example {
                    void run() {
                        assertEquals(
                                new Command.Init(
                                        new InitRequest(
                                                "com.example.application",
                                                Optional.of(25),
                                                Optional.of("com.example.application.Main"),
                                                true,
                                                List.of(
                                                        new RequireRequest.RuntimeAccess("enableNativeAccess", "org.example.nativebinding"),
                                                        new RequireRequest.RuntimeAccess("enableNativeAccess", "org.example.other"),
                                                        new RequireRequest.RuntimeAccess("enableFinalFieldMutation", "org.example.model"),
                                                        new RequireRequest.RuntimeAccess("addExports", "jdk.compiler/com.sun.tools.javac.tree=com.example.application"),
                                                        new RequireRequest.RuntimeAccess("addOpens", "java.base/java.lang=com.example.application"),
                                                        new RequireRequest.RuntimeAccess("addOpens", "java.base/java.lang=org.example.friend")))),
                                commandLine.command());
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void carriesArrayInitializerStructureThroughNestedCalls() {
        String input = "class Example{void run(){var duplicateRelease=assertThrows(IllegalArgumentException.class,()->CommandLine.parse(temporaryDirectory,new String[]{\"init\",\"--release\",\"21\",\"--release=25\",\"com.example.app\"}));var duplicateMainClass=assertThrows(IllegalArgumentException.class,()->CommandLine.parse(temporaryDirectory,new String[]{\"init\",\"--main-class\",\"com.example.Main\",\"--main-class=com.example.Other\",\"com.example.app\"}));}}";
        String expected =
                """
                class Example {
                    void run() {
                        var duplicateRelease = assertThrows(
                                IllegalArgumentException.class,
                                () -> CommandLine.parse(temporaryDirectory,
                                        new String[] {"init", "--release", "21", "--release=25", "com.example.app"}));
                        var duplicateMainClass = assertThrows(
                                IllegalArgumentException.class,
                                () -> CommandLine.parse(temporaryDirectory,
                                        new String[] {"init", "--main-class", "com.example.Main", "--main-class=com.example.Other", "com.example.app"}));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void usesBrokenListInsteadOfWrappingParametersTwice() {
        String input = "class Example{void invoke(First first,Second second,Third third,Fourth fourth,Fifth fifth,Sixth sixth,Seventh seventh){}}";
        String expected =
                """
                class Example {
                    void invoke(
                            First first,
                            Second second,
                            Third third,
                            Fourth fourth,
                            Fifth fifth,
                            Sixth sixth,
                            Seventh seventh) {}
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void prefersEnclosingListLayoutToNestedChainAlignment() {
        String input = "class Example{void run(){assertEquals(moduleRoot.toAbsolutePath().normalize(),commandLine.workingDirectory());}}";
        String expected =
                """
                class Example {
                    void run() {
                        assertEquals(moduleRoot.toAbsolutePath().normalize(),
                                commandLine.workingDirectory());
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void prefersEnclosingListLayoutToNestedListAndChainAlignment() {
        String input = "class Example{void run(){assertEquals(List.of(\"--module-source-path\",moduleRoot.resolve(\"src\").toString(),\"-m\",\"com.example.app\"),commandLine.resolutionArguments());}}";
        String expected =
                """
                class Example {
                    void run() {
                        assertEquals(
                                List.of("--module-source-path", moduleRoot.resolve("src").toString(),
                                        "-m", "com.example.app"),
                                commandLine.resolutionArguments());
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksAnEnclosingListWhenSiblingExpressionsAreFarApart() {
        String input = "class Example{void run(){assertEquals(List.of(\"--module-path\",\"/path with spaces\",\"--add-modules\",\"a,b\",\"#value\"),ArgumentFiles.parse(\"--module-path\\n\\\"/path with spaces\\\"\\n--add-modules\\na,b\\n\\\"#value\\\"\\n\"));}}";
        String expected =
                """
                class Example {
                    void run() {
                        assertEquals(
                                List.of("--module-path", "/path with spaces", "--add-modules", "a,b", "#value"),
                                ArgumentFiles.parse("--module-path\\n\\\"/path with spaces\\\"\\n--add-modules\\na,b\\n\\\"#value\\\"\\n"));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksAListWhoseSimpleMembersHaveExcessiveScanningDistance() {
        String input = "class Example{private static final Set<String> COMPILE_OPTIONS=Set.of(\"add-exports\",\"enable-preview\",\"module-path\",\"module-source-path\",\"module-version\",\"module=list\",\"patch-module\",\"processor-module-path\",\"release\",\"upgrade-module-path\");private static final Set<String> SOURCE_OPTIONS=Set.of(\"add-exports\",\"enable-preview\",\"module=list\",\"module-path\",\"module-source-path\",\"release\");}";
        String expected =
                """
                class Example {
                    private static final Set<String> COMPILE_OPTIONS = Set.of(
                            "add-exports",
                            "enable-preview",
                            "module-path",
                            "module-source-path",
                            "module-version",
                            "module=list",
                            "patch-module",
                            "processor-module-path",
                            "release",
                            "upgrade-module-path");
                    private static final Set<String> SOURCE_OPTIONS = Set.of("add-exports", "enable-preview", "module=list", "module-path", "module-source-path", "release");
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void wrapsAnArrayWhoseLiteralMembersHaveLongScanningDistance() {
        String input = "class Example{void run(){String[] options={\"--module-path=/some/path\",\"--add-modules=com.example.app\",\"--add-exports=java.base/java.lang=com.example\",\"--enable-preview\",\"--patch-module=com.example.app=classes\",\"--upgrade-module-path=/some/upgrade/path\"};}}";
        String expected =
                """
                class Example {
                    void run() {
                        String[] options = {"--module-path=/some/path", "--add-modules=com.example.app", "--add-exports=java.base/java.lang=com.example",
                                "--enable-preview", "--patch-module=com.example.app=classes", "--upgrade-module-path=/some/upgrade/path"};
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksBeforeConditionalArgumentWithComposedClauses() {
        String input = "class E{void f(){use((file!=null?file:path()),(incremental?loaded(file):0));}}";
        String expected =
                """
                class E {
                    void f() {
                        use((file != null ? file : path()),
                                (incremental ? loaded(file) : 0));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void countsFormatStringWithConditionalArguments() {
        String input = "class E{String f(boolean virtual,boolean setter){return String.format(\"(%s%s)%s\",(virtual?\"R\":\"\"),(setter?\"T\":\"\"),(setter?\"V\":\"T\"));}}";
        String expected =
                """
                class E {
                    String f(boolean virtual, boolean setter) {
                        return String.format("(%s%s)%s", (virtual ? "R" : ""),
                                (setter ? "T" : ""), (setter ? "V" : "T"));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsLiteralConditionalArgumentsTogether() {
        String input = "class E{void f(){use((pallet?120:150),(pallet?39:65));}}";
        String expected =
                """
                class E {
                    void f() {
                        use((pallet ? 120 : 150), (pallet ? 39 : 65));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksAfterSixSimpleArguments() {
        String input = "class Example{void run(){invoke(one,two,three,four,five,six,seven);}}";
        String expected =
                """
                class Example {
                    void run() {
                        invoke(one, two, three, four, five, six,
                                seven);
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsSixSimpleArgumentsTogether() {
        String input = "class Example{void run(){invoke(one,two,three,four,five,six);}}";
        String expected =
                """
                class Example {
                    void run() {
                        invoke(one, two, three, four, five, six);
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksArithmeticAtTheHigherLevelOperator() {
        String input = "class Example{void run(){result=firstLongValue*(secondLongValue+thirdLongValue-fourthLongValue)+4*fifthLongValue+8*sixthLongValue+12*seventhLongValue;}}";
        String expected =
                """
                class Example {
                    void run() {
                        result = firstLongValue * (secondLongValue + thirdLongValue - fourthLongValue)
                                 + 4 * fifthLongValue
                                 + 8 * sixthLongValue
                                 + 12 * seventhLongValue;
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void usesContinuationIndentWhenArithmeticAlignmentIsTooDeep() {
        String input = "class Example{void run(){extraordinarilyLongVariableName=firstLongValue*(secondLongValue+thirdLongValue-fourthLongValue)+4*fifthLongValue+8*sixthLongValue+12*seventhLongValue;}}";
        String expected =
                """
                class Example {
                    void run() {
                        extraordinarilyLongVariableName = firstLongValue * (secondLongValue + thirdLongValue - fourthLongValue)
                                + 4 * fifthLongValue
                                + 8 * sixthLongValue
                                + 12 * seventhLongValue;
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsShallowNestedMethodCallWithAssignment() {
        String input = "class Example{void run(){result=someMethod1(firstLongExpression,someMethod2(secondLongExpression,thirdLongExpression));}}";
        String expected =
                """
                class Example {
                    void run() {
                        result = someMethod1(firstLongExpression, someMethod2(secondLongExpression, thirdLongExpression));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksBeforeComposedExpressionLambdaArgument() {
        String input = "class E{Object f(){return of(State::new,factory(),(state,downstream)->state.flush(MAX,downstream));}}";
        String expected =
                """
                class E {
                    Object f() {
                        return of(State::new, factory(),
                                (state, downstream) -> state.flush(MAX, downstream));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsShallowExpressionLambdaArgumentTogether() {
        String input = "class E{Object f(){return of(State::new,value->use(value));}}";
        String expected =
                """
                class E {
                    Object f() {
                        return of(State::new, value -> use(value));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsShortExpressionLambdaBodiesInline() {
        String input = "class Example{void run(){values.stream().map(value->value.name()).forEach(name->use(name));}}";
        String expected =
                """
                class Example {
                    void run() {
                        values.stream()
                                .map(value -> value.name())
                                .forEach(name -> use(name));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksNestedExpressionLambdaAfterArrow() {
        String input = "class Example{Object run(){return create((a,b,c)->left(a,b,r->right(r,c)));}}";
        String expected =
                """
                class Example {
                    Object run() {
                        return create((a, b, c) ->
                                left(a, b, r -> right(r, c)));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsShallowExpressionLambdaInline() {
        String input = "class Example{Object run(){return create((a,b,c)->use(a,b,c));}}";
        String expected =
                """
                class Example {
                    Object run() {
                        return create((a, b, c) -> use(a, b, c));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsShallowExpressionLambdaBodyTogetherRegardlessOfLength() {
        String input = "class Example{void run(){forEachEntry((packagePath,modules)->processPreviewDirectory(moduleConfiguration,packagePath,modules));}}";
        String expected =
                """
                class Example {
                    void run() {
                        forEachEntry((packagePath, modules) -> processPreviewDirectory(moduleConfiguration, packagePath, modules));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksBeforeLambdaWithComposedBlockBody() {
        String input = "class E{Object f(){return executor(1,task->{var thread=create(task);thread.start();return thread;});}}";
        String expected =
                """
                class E {
                    Object f() {
                        return executor(1,
                                task -> {
                                    var thread = create(task);
                                    thread.start();
                                    return thread;
                                });
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void indentsBlockLambdaAsABlock() {
        String input = "class Example{void run(){forEachEntry((key,value)->{use(key);use(value);});}}";
        String expected =
                """
                class Example {
                    void run() {
                        forEachEntry((key, value) -> {
                            use(key);
                            use(value);
                        });
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void doesNotBreakAFittingCallBecauseAnArgumentHasABlock() {
        String input = "class Example{void run(){apply(firstArgument,value->{use(value);});}}";
        String expected =
                """
                class Example {
                    void run() {
                        apply(firstArgument, value -> {
                            use(value);
                        });
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void continuesAfterAMultilineArgumentWithoutDependingOnWidth() {
        String input = "class Example{void run(){apply(first,value->{use(value);},last);}}";
        String expected =
                """
                class Example {
                    void run() {
                        apply(first, value -> {
                            use(value);
                        },
                                last);
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void doesNotBreakAFittingCallBecauseAnArgumentHasAnAnonymousClassBody() {
        String input = "class Example{void run(){register(firstArgument,new Runnable(){@Override public void run(){work();}});}}";
        String expected =
                """
                class Example {
                    void run() {
                        register(firstArgument, new Runnable() {
                            @Override
                            public void run() {
                                work();
                            }
                        });
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void doesNotBreakAFittingCallBecauseAnArgumentIsASwitchExpression() {
        String input = "class Example{void run(){consume(firstArgument,switch(state){case READY->ready();default->fallback();});}}";
        String expected =
                """
                class Example {
                    void run() {
                        consume(firstArgument, switch (state) {
                            case READY -> ready();
                            default -> fallback();
                        });
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksBeforeAndAfterAnArgumentContainingABlock() {
        String input = "class E{Object f(){return make(seed,wrap(value->{use(value);}),finish);}}";
        String expected =
                """
                class E {
                    Object f() {
                        return make(seed,
                                wrap(value -> {
                                    use(value);
                                }),
                                finish);
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksAfterAMultilineArgumentBeforeTheNextArgument() {
        String input = "class Example{Object run(){return Gatherer.ofSequential(State::new,Integrator.ofGreedy((state,element,downstream)->{state.value=folder.apply(state.value,element);return true;}),(state,downstream)->downstream.push(state.value));}}";
        String expected =
                """
                class Example {
                    Object run() {
                        return Gatherer.ofSequential(State::new,
                                Integrator.ofGreedy((state, element, downstream) -> {
                                    state.value = folder.apply(state.value, element);
                                    return true;
                                }),
                                (state, downstream) -> downstream.push(state.value));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsAFittingConditionalLambdaHeaderTogether() {
        String input = "class Example{void run(){result=condition?defaultFinisher():(state,downstream)->{finish(state,downstream);};}}";
        String expected =
                """
                class Example {
                    void run() {
                        result = condition ? defaultFinisher() : (state, downstream) -> {
                            finish(state, downstream);
                        };
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsShallowNestedExpressionLambdaTogether() {
        String input = "class Example{Object run(){return create((unused,element,downstream)->leftIntegrator.integrate(null,element,result->rightIntegratorWithLongName.integrateIntoDownstream(null,result,downstreamWithLongName)));}}";
        String expected =
                """
                class Example {
                    Object run() {
                        return create((unused, element, downstream) ->
                                leftIntegrator.integrate(null, element, result -> rightIntegratorWithLongName.integrateIntoDownstream(null, result, downstreamWithLongName)));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksBetweenChainedCallsWhoseArgumentListsAlreadyWrap() {
        String input = "class Example{void run(){int result=new CommandRunner(ModuleLayer.boot(),tools,new ToolCatalog(List.of()),()->null).run(commandLine,InputStream.nullInputStream(),new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));}}";
        String expected =
                """
                class Example {
                    void run() {
                        int result = new CommandRunner(ModuleLayer.boot(), tools, new ToolCatalog(List.of()), () -> null)
                                .run(commandLine, InputStream.nullInputStream(), new PrintStream(new ByteArrayOutputStream()),
                                        new PrintStream(new ByteArrayOutputStream()));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksChainAfterThreeInvocations() {
        String input = "class Example{void run(){var logLines=log.toString().lines().toList();}}";
        String expected =
                """
                class Example {
                    void run() {
                        var logLines = log.toString()
                                          .lines()
                                          .toList();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsTwoInvocationChainTogether() {
        String input = "class Example{void run(){var text=log.toString().trim();}}";
        String expected =
                """
                class Example {
                    void run() {
                        var text = log.toString().trim();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void breaksTwoInvocationChainNestedInAnotherCall() {
        String input = "class Example{void run(){downstream.push(SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArrayNullsAllowed(window));}}";
        String expected =
                """
                class Example {
                    void run() {
                        downstream.push(SharedSecrets.getJavaUtilCollectionAccess()
                                .listFromTrustedArrayNullsAllowed(window));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void alignsBrokenMethodChainWithFirstSelector() {
        String input = "class Example{void run(){var logLines=log.toString().lines().map(line->normalize(line,configuration)).filter(line->!line.isEmpty()).toList();}}";
        String expected =
                """
                class Example {
                    void run() {
                        var logLines = log.toString()
                                          .lines()
                                          .map(line -> normalize(line, configuration))
                                          .filter(line -> !line.isEmpty())
                                          .toList();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void usesContinuationIndentWhenMethodChainAlignmentIsTooDeep() {
        String input = "class Example{void run(){var result=extraordinarilyLongReceiverName.firstOperationWithLongName().secondOperationWithLongName().thirdOperationWithLongName();}}";
        String expected =
                """
                class Example {
                    void run() {
                        var result = extraordinarilyLongReceiverName.firstOperationWithLongName()
                                .secondOperationWithLongName()
                                .thirdOperationWithLongName();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void measuresMethodChainAlignmentFromTheEnclosingLine() {
        String input = "class Example{void run(){extraordinarilyLongReceiverName.firstOperationWithLongName().secondOperationWithLongName().thirdOperationWithLongName();}}";
        String expected =
                """
                class Example {
                    void run() {
                        extraordinarilyLongReceiverName.firstOperationWithLongName()
                                .secondOperationWithLongName()
                                .thirdOperationWithLongName();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void continuesConstructorRootedMethodChain() {
        String input = "class Example{Object build(){return new ImageLocationWriter(strings).addAttribute(ATTRIBUTE_MODULE,moduleName).addAttribute(ATTRIBUTE_PARENT,parentOffset).addAttribute(ATTRIBUTE_BASE,baseOffset).build();}}";
        String expected =
                """
                class Example {
                    Object build() {
                        return new ImageLocationWriter(strings)
                                .addAttribute(ATTRIBUTE_MODULE, moduleName)
                                .addAttribute(ATTRIBUTE_PARENT, parentOffset)
                                .addAttribute(ATTRIBUTE_BASE, baseOffset)
                                .build();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsSixArgumentsTogetherWithinMethodChain() {
        String input = "class Example{Object build(){return new ImageLocationWriter(strings).addAttribute(ATTRIBUTE_MODULE,moduleName,contentOffset,compressedSize,uncompressedSize,previewFlags).addAttribute(ATTRIBUTE_PARENT,parentName).build();}}";
        String expected =
                """
                class Example {
                    Object build() {
                        return new ImageLocationWriter(strings)
                                .addAttribute(ATTRIBUTE_MODULE, moduleName, contentOffset, compressedSize, uncompressedSize, previewFlags)
                                .addAttribute(ATTRIBUTE_PARENT, parentName)
                                .build();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsShallowNestedCallArgumentsTogetherWithinMethodChain() {
        String input = "class Example{Object collect(){return values.stream().filter(value->value.isAvailable()).collect(Collectors.toMap(primaryKeyMapperWithLongName,secondaryValueMapperWithLongName,mergeFunctionWithLongName,LinkedHashMap::new));}}";
        String expected =
                """
                class Example {
                    Object collect() {
                        return values.stream()
                                .filter(value -> value.isAvailable())
                                .collect(Collectors.toMap(primaryKeyMapperWithLongName, secondaryValueMapperWithLongName, mergeFunctionWithLongName, LinkedHashMap::new));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void composesConditionalExpressionWithMethodChain() {
        String input = "class Example{void run(){result=(exponent<MAXIMUM_CACHE_EXPONENT&&remainingExponent>0?POWERS[exponent]:LARGE_POWERS[exponent-MAXIMUM_CACHE_EXPONENT]).multiply(POWERS[remainingExponent]);}}";
        String expected =
                """
                class Example {
                    void run() {
                        result = (exponent < MAXIMUM_CACHE_EXPONENT && remainingExponent > 0
                                ? POWERS[exponent]
                                : LARGE_POWERS[exponent - MAXIMUM_CACHE_EXPONENT])
                                .multiply(POWERS[remainingExponent]);
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsSelectorWithSimpleConditionalExpression() {
        String input = "class E{Object f(){return (ready?left:right).value();}}";
        String expected =
                """
                class E {
                    Object f() {
                        return (ready ? left : right).value();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsFirstSelectorWithParenthesizedChainRoot() {
        String input = "class Example{Object property(){return ((HeadlessToolkit)this).getUnderlyingToolkit().getDesktopProperty(propertyName).orElseGet(()->defaultDesktopProperty(propertyName));}}";
        String expected =
                """
                class Example {
                    Object property() {
                        return ((HeadlessToolkit) this).getUnderlyingToolkit()
                                .getDesktopProperty(propertyName)
                                .orElseGet(() -> defaultDesktopProperty(propertyName));
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void continuesMethodChainAfterReturnExpression() {
        String input = "class Example{Object run(){return ProcessHandleImpl.completion(pid(),false).handleAsync((exitStatus,unusedThrowable)->this).thenApply(process->process.toHandle());}}";
        String expected =
                """
                class Example {
                    Object run() {
                        return ProcessHandleImpl.completion(pid(), false)
                                .handleAsync((exitStatus, unusedThrowable) -> this)
                                .thenApply(process -> process.toHandle());
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void usesContinuationIndentForDeepMethodChainAlignment() {
        String input = "class Example{void run(){byte[] time=BigInteger.valueOf(new Date().getTime()).add(new BigInteger(\"11644473600000\")).multiply(BigInteger.valueOf(10000)).toByteArray();}}";
        String expected =
                """
                class Example {
                    void run() {
                        byte[] time = BigInteger.valueOf(new Date().getTime())
                                .add(new BigInteger("11644473600000"))
                                .multiply(BigInteger.valueOf(10000))
                                .toByteArray();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsLongStringLiteralIntact() {
        String input = "class Example{void check(){if(firstCondition){if(secondCondition){throw new CertificateException(\"QUIC only supports SunJSSE trust managers for this transport implementation\");}}}}";
        String expected =
                """
                class Example {
                    void check() {
                        if (firstCondition) {
                            if (secondCondition) {
                                throw new CertificateException("QUIC only supports SunJSSE trust managers for this transport implementation");
                            }
                        }
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }

    @Test
    void keepsShallowLambdaBodyTogetherWithinBrokenMethodChain() {
        String input = "class Example{Object run(){return values.stream().map(value->transformValueWithConfiguration(value,configuration,additionalConfiguration,environment)).filter(value->value.isAvailable()).toList();}}";
        String expected =
                """
                class Example {
                    Object run() {
                        return values.stream()
                                .map(value -> transformValueWithConfiguration(value, configuration, additionalConfiguration, environment))
                                .filter(value -> value.isAvailable())
                                .toList();
                    }
                }
                """;

        String formatted = JfmtTestSupport.format(input);

        assertEquals(expected, formatted);
        assertEquals(formatted, JfmtTestSupport.format(formatted));
    }
}
