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
import java.io.File;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.OptionChecker;
import javax.tools.Tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JfmtToolTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void declaresFormattingToolAndWarmup() throws Exception {
        Tool tool = tool();
        try (var input = tool.getClass()
                             .getModule()
                             .getResourceAsStream("META-INF/com.netflix.tools/tools/jfmt.properties")) {
            assertNotNull(input);
            var properties = new Properties();
            properties.load(input);
            assertEquals("fmt", properties.getProperty("type"));
            assertEquals(
                    "module-path,upgrade-module-path,module-source-path,module=list,add-modules,patch-module,release,enable-preview,add-exports",
                    properties.getProperty("options"));
            assertEquals("true", properties.getProperty("compile-time"));
            assertEquals("--aot-warmup", properties.getProperty("warmup"));
        }
    }

    @Test
    void bundlesGoogleJavaFormatLicense() throws Exception {
        try (var input = tool().getClass()
                               .getModule()
                               .getResourceAsStream("META-INF/LICENSE.google-java-format")) {
            assertNotNull(input);
            String license = new String(input.readAllBytes(), UTF_8);
            assertTrue(license.startsWith("The following Apache 2.0 license applies to all code in this package"));
        }
    }

    @Test
    void bundlesCommonMarkLicense() throws Exception {
        try (var input = tool().getClass()
                               .getModule()
                               .getResourceAsStream("META-INF/LICENSE.commonmark")) {
            assertNotNull(input);
            String license = new String(input.readAllBytes(), UTF_8);
            assertTrue(license.startsWith("Copyright (c) 2015, Atlassian Pty Ltd"));
        }
    }

    @Test
    void exposesOptionContract() {
        assertOptionContract(tool());
        assertOptionContract(toolProvider());
    }

    @Test
    void expandsArgumentFiles() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "class Example{int value;}");
        Path arguments = write(temporaryDirectory.resolve("jfmt.args"), '"' + source.toString() + "\"\n");

        Invocation invocation = run("", "@" + arguments);

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals("class Example {\n    int value;\n}\n", Files.readString(source));
    }

    @Test
    void formatsMultipleFilesIdempotently() throws Exception {
        var files = new String[64];
        for (int i = 0; i < files.length; i++) {
            Path source = write(
                    temporaryDirectory.resolve("Example" + i + ".java"),
                    """
                    class Example%d {
                        int run(String operation) {
                            return switch (operation) {
                                case "install", "deploy", "deploy-central" -> new MavenDeploymentCommand(tools, definitions, javadocOptions)
                                        .run(commandLine, moduleSourcePath, in, out, err);
                                default -> 0;
                            };
                        }
                    }
                    """.formatted(i));
            files[i] = source.toString();
        }

        Invocation formatted = run("", files);
        var checkArguments = new String[files.length + 1];
        checkArguments[0] = "--check";
        System.arraycopy(files, 0, checkArguments, 1, files.length);
        Invocation checked = run("", checkArguments);

        assertEquals(0, formatted.exitCode(), formatted.error());
        assertEquals(0, checked.exitCode(), checked.error());
    }

    @Test
    void acceptsOptionsAfterFileOperands() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "class Example{int value;}");

        Invocation invocation = run("", source.toString(), "--check");

        assertEquals(1, invocation.exitCode());
        assertEquals(source + ": not formatted\n", invocation.error());
        assertEquals("class Example{int value;}", Files.readString(source));
    }

    @Test
    void providesHelpAndDelegatedCompletion() {
        Invocation help = run("", "--help");
        Invocation completion = run("", "__complete", "--mod");

        assertEquals(0, help.exitCode(), help.error());
        assertTrue(help.output().startsWith("Usage: jfmt [OPTIONS] [FILE...]\n"),
                help.output());
        assertEquals("", help.error());
        assertEquals(0, completion.exitCode(), completion.error());
        assertEquals(
                List.of("--module\tSelect source modules", "--module-path\tWhere to find application modules", "--module-source-path\tWhere to find module source trees", ":0"),
                completion.output()
                          .lines()
                          .toList());
        assertEquals("", completion.error());
    }

    @Test
    void printsVersion() {
        Invocation invocation = run("", "--version");
        Tool tool = tool();
        String version = tool.getClass()
                .getModule()
                .getDescriptor()
                .rawVersion()
                .orElse("dev");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals("jfmt " + version + "\n", invocation.output());
        assertEquals("", invocation.error());
    }

    @Test
    void normalizesImportsWhenSourcesAreAttributable() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "import java.util.*;import static java.util.Collections.*;class Example{java.time.Duration duration;List<String> values=emptyList();Map<String,String> index;}");

        Invocation invocation = run("", source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                import java.time.Duration;
                import java.util.List;
                import java.util.Map;

                import static java.util.Collections.emptyList;

                class Example {
                    Duration duration;
                    List<String> values = emptyList();
                    Map<String, String> index;
                }
                """,
                Files.readString(source));
    }

    @Test
    void doesNotImportNestedTypesFromTheSameTopLevelClass() throws Exception {
        Path source = write(
                temporaryDirectory.resolve("Example.java"),
                """
                package example;

                class Example {
                    Container.Nested value;

                    static class Container {
                        static class Nested {}
                    }
                }
                """);

        Invocation invocation = run("", source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                package example;

                class Example {
                    Container.Nested value;

                    static class Container {
                        static class Nested {}
                    }
                }
                """,
                Files.readString(source));
    }

    @Test
    void normalizesQualifiedRecordComponentWithCompactConstructor() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "record Example(java.util.Optional<String> value){Example{if(value.isEmpty())throw new IllegalArgumentException();}}");

        Invocation invocation = run("", source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                import java.util.Optional;

                record Example(Optional<String> value) {
                    Example {
                        if (value.isEmpty()) {
                            throw new IllegalArgumentException();
                        }
                    }
                }
                """,
                Files.readString(source));
    }

    @Test
    void runsWithJdkCompilerDefinedInTheSameChildLayer() throws Exception {
        var parent = getClass().getModule().getLayer();
        var jfmtReference = parent.configuration()
                .findModule("com.netflix.tools.jfmt")
                .orElseThrow()
                .reference();
        var compilerReference = parent.configuration()
                .findModule("jdk.compiler")
                .orElseThrow()
                .reference();
        var finder = finder(jfmtReference, compilerReference);
        var configuration = parent.configuration()
                .resolve(finder, ModuleFinder.of(), Set.of("com.netflix.tools.jfmt", "jdk.compiler"));
        var controller = ModuleLayer.defineModulesWithOneLoader(configuration, List.of(parent),
                ClassLoader.getSystemClassLoader());
        var layer = controller.layer();
        var jfmtModule = layer.findModule("com.netflix.tools.jfmt").orElseThrow();
        var compilerModule = layer.findModule("jdk.compiler").orElseThrow();
        for (var packageName : List.of(
                "com.sun.tools.javac.api",
                "com.sun.tools.javac.code",
                "com.sun.tools.javac.file",
                "com.sun.tools.javac.model",
                "com.sun.tools.javac.parser",
                "com.sun.tools.javac.tree",
                "com.sun.tools.javac.util")) {
            controller.addExports(compilerModule, packageName, jfmtModule);
        }
        var tool = ServiceLoader.load(layer, Tool.class).stream()
                .filter(provider -> provider.type().getModule() == jfmtModule)
                .findFirst()
                .map(Provider::get)
                .orElseThrow();
        var source = write(temporaryDirectory.resolve("Layered.java"), "class Layered{int value;}");
        var output = new ByteArrayOutputStream();
        var error = new ByteArrayOutputStream();
        var thread = Thread.currentThread();
        var contextClassLoader = thread.getContextClassLoader();
        int exitCode;
        try {
            thread.setContextClassLoader(layer.findLoader("com.netflix.tools.jfmt"));
            exitCode = tool.run(new ByteArrayInputStream(new byte[0]), output, error, source.toString());
        } finally {
            thread.setContextClassLoader(contextClassLoader);
        }

        assertEquals(0, exitCode, error.toString(UTF_8));
        assertEquals("class Layered {\n    int value;\n}\n", Files.readString(source));
    }

    @Test
    void expandsWildcardImportsUsedFromDocumentation() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "import java.util.*;/** See {@link List}. */class Example{}");

        Invocation invocation = run("", source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                import java.util.List;

                /** See {@link List}. */
                class Example {}
                """,
                Files.readString(source));
    }

    @Test
    void usesTheCompilationClassPathForAttribution() throws Exception {
        Path dependency = write(temporaryDirectory.resolve("dependency/Widget.java"), "package dependency;public class Widget{}");
        Path classes = Files.createDirectories(temporaryDirectory.resolve("classes"));
        int compilation = javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(),
                dependency.toString());
        assertEquals(0, compilation);
        Path source = write(temporaryDirectory.resolve("application/Example.java"), "package application;class Example{dependency.Widget widget;}");

        Invocation invocation = run("", "--class-path", classes.toString(), source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                package application;

                import dependency.Widget;

                class Example {
                    Widget widget;
                }
                """,
                Files.readString(source));
    }

    @Test
    void usesSourcePathWithoutFormattingLookupSources() throws Exception {
        String dependencyInput = "package dependency;public class Widget{}";
        Path root = temporaryDirectory.resolve("lookup");
        Path dependency = write(root.resolve("dependency/Widget.java"), dependencyInput);
        Path source = write(temporaryDirectory.resolve("application/Example.java"), "package application;class Example{dependency.Widget widget;}");

        Invocation invocation = run("", "--source-path", root.toString(), source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedWidgetUser(), Files.readString(source));
        assertEquals(dependencyInput, Files.readString(dependency));
    }

    @Test
    void usesSourceArchiveWithoutModifyingIt() throws Exception {
        Path archive = sourceArchive();
        byte[] originalArchive = Files.readAllBytes(archive);
        Path source = write(temporaryDirectory.resolve("application/Example.java"), "package application;class Example{dependency.Widget widget;}");

        Invocation invocation = run("", "--source-path", archive.toString(), source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedWidgetUser(), Files.readString(source));
        assertArrayEquals(originalArchive, Files.readAllBytes(archive));
    }

    @Test
    void formatsSourcePathByDefault() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "class Example{int value;}");
        Path nested = write(temporaryDirectory.resolve("example/Nested.java"), unformattedModuleClass("Nested"));
        Path resource = write(temporaryDirectory.resolve("example/resource.txt"), "leave this alone");

        Invocation invocation = run("", "--source-path", temporaryDirectory.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals("class Example {\n    int value;\n}\n", Files.readString(source));
        assertEquals(formattedModuleClass("Nested"), Files.readString(nested));
        assertEquals("leave this alone", Files.readString(resource));
    }

    @Test
    void checksSourcePathWithoutChangingFiles() throws Exception {
        Path first = write(temporaryDirectory.resolve("example/First.java"), unformattedModuleClass("First"));
        Path second = write(temporaryDirectory.resolve("example/Second.java"), unformattedModuleClass("Second"));

        Invocation invocation = run("", "--source-path", temporaryDirectory.toString(), "--check");

        assertEquals(1, invocation.exitCode());
        assertEquals(Set.of(first.toRealPath() + ": not formatted", second.toRealPath() + ": not formatted"),
                Set.copyOf(invocation.error().lines().toList()));
        assertEquals(unformattedModuleClass("First"), Files.readString(first));
        assertEquals(unformattedModuleClass("Second"), Files.readString(second));
    }

    @Test
    void formatsMultipleSourceDirectories() throws Exception {
        Path firstRoot = temporaryDirectory.resolve("first");
        Path secondRoot = temporaryDirectory.resolve("second");
        Path first = write(firstRoot.resolve("example/First.java"), unformattedModuleClass("First"));
        Path second = write(secondRoot.resolve("example/Second.java"), unformattedModuleClass("Second"));

        Invocation invocation = run("", "--source-path", firstRoot + File.pathSeparator + secondRoot);

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedModuleClass("First"), Files.readString(first));
        assertEquals(formattedModuleClass("Second"), Files.readString(second));
    }

    @Test
    void repeatedSourcePathReplacesPreviousSelection() throws Exception {
        Path firstRoot = temporaryDirectory.resolve("first");
        Path secondRoot = temporaryDirectory.resolve("second");
        Path first = write(firstRoot.resolve("example/First.java"), unformattedModuleClass("First"));
        Path second = write(secondRoot.resolve("example/Second.java"), unformattedModuleClass("Second"));

        Invocation invocation = run("", "--source-path", firstRoot.toString(), "-sourcepath", secondRoot.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(unformattedModuleClass("First"), Files.readString(first));
        assertEquals(formattedModuleClass("Second"), Files.readString(second));
    }

    @Test
    void formatsSourceDirectoriesButOnlyUsesArchivesForLookup() throws Exception {
        Path archive = sourceArchive();
        byte[] originalArchive = Files.readAllBytes(archive);
        Path root = temporaryDirectory.resolve("sources");
        Path source = write(root.resolve("application/Example.java"), "package application;class Example{dependency.Widget widget;}");

        Invocation invocation = run("", "--source-path", archive + File.pathSeparator + root);

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedWidgetUser(), Files.readString(source));
        assertArrayEquals(originalArchive, Files.readAllBytes(archive));
    }

    @Test
    void sourceArchivesAloneDoNotSelectFiles() throws Exception {
        Path archive = sourceArchive();
        byte[] originalArchive = Files.readAllBytes(archive);

        Invocation invocation = run("", "--source-path", archive.toString());

        assertEquals(1, invocation.exitCode());
        assertEquals("no Java source files were found\n", invocation.error());
        assertArrayEquals(originalArchive, Files.readAllBytes(archive));
    }

    @Test
    void binaryPathsDoNotSelectSources() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "class Example{int value;}");

        Invocation invocation = run("", "--class-path", temporaryDirectory.toString(), "--module-path", temporaryDirectory.toString());

        assertEquals(1, invocation.exitCode());
        assertEquals("no Java source files were found\n", invocation.error());
        assertEquals("class Example{int value;}", Files.readString(source));
    }

    @Test
    void stdinOverridesSourcePathDiscovery() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "class Example{int value;}");

        Invocation invocation = run("class Input{}", "--source-path", temporaryDirectory.toString(), "-");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals("class Input {}\n", invocation.output());
        assertEquals("class Example{int value;}", Files.readString(source));
    }

    @Test
    void emptySourceDirectoryHasNoFormattingInputs() throws Exception {
        Invocation invocation = run("", "--source-path", temporaryDirectory.toString());

        assertEquals(1, invocation.exitCode());
        assertEquals("no Java source files were found\n", invocation.error());
    }

    @Test
    void attributesSourceFilesTogether() throws Exception {
        Path dependency = write(temporaryDirectory.resolve("dependency/Widget.java"), "package dependency;public class Widget{}");
        Path source = write(temporaryDirectory.resolve("application/Example.java"), "package application;class Example{dependency.Widget widget;}");

        Invocation invocation = run("", dependency.toString(), source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                package application;

                import dependency.Widget;

                class Example {
                    Widget widget;
                }
                """,
                Files.readString(source));
    }

    @Test
    void normalizesImportsWithoutAttributingMethodBodies() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "import java.util.*;class Example{List<String> values;void broken(){missing();}}");

        Invocation invocation = run("", source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                import java.util.List;

                class Example {
                    List<String> values;

                    void broken() {
                        missing();
                    }
                }
                """,
                Files.readString(source));
    }

    @Test
    void failsWhenAnAttributableSourceContainsAnUnresolvedType() throws Exception {
        String input = "import java.util.*;class Example{Missing missing;java.time.Duration duration;List<String> values;}";
        Path source = write(temporaryDirectory.resolve("Example.java"), input);

        Invocation invocation = run("", source.toString());

        assertEquals(1, invocation.exitCode());
        assertTrue(invocation.error().contains("cannot find symbol"),
                invocation.error());
        assertEquals(input, Files.readString(source));
    }

    @Test
    void usesSyntaxOnlyFormattingWhenModuleRequirementsAreUnsatisfied() throws Exception {
        Path module = Files.createDirectories(temporaryDirectory.resolve("com.example.application"));
        write(module.resolve("module-info.java"), "module com.example.application { requires com.example.missing; }");
        Path source = write(module.resolve("example/Example.java"), "package example;import java.util.*;class Example{java.time.Duration duration;List<String> values;}");

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString(), "--module", "com.example.application");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                package example;

                import java.util.*;

                class Example {
                    java.time.Duration duration;
                    List<String> values;
                }
                """,
                Files.readString(source));
    }

    @Test
    void usesSyntaxOnlyFormattingForExplicitModuleSourceWithMissingRequirements() throws Exception {
        Path module = temporaryDirectory.resolve("com.example.application");
        Path descriptor = write(module.resolve("module-info.java"), "module com.example.application { requires com.example.missing; }");
        Path source = write(module.resolve("example/Example.java"), "package example;import java.util.*;class Example{java.time.Duration duration;List<String> values;}");

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString(), source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedWithoutImportNormalization(), Files.readString(source));
        assertEquals("module com.example.application { requires com.example.missing; }", Files.readString(descriptor));
    }

    @Test
    void usesSyntaxOnlyFormattingAcrossModuleSourceRootsWithMissingRequirements() throws Exception {
        Path module = temporaryDirectory.resolve("main");
        write(module.resolve("module-info.java"), "module com.example.application { requires com.example.missing; }");
        Path generatedRoot = temporaryDirectory.resolve("generated");
        Path source = write(generatedRoot.resolve("example/Example.java"), "package example;import java.util.*;class Example{java.time.Duration duration;List<String> values;}");

        Invocation invocation = run("", "--module-source-path", "com.example.application=" + module + File.pathSeparator + generatedRoot,
                "--module", "com.example.application");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedWithoutImportNormalization(), Files.readString(source));
    }

    @Test
    void normalizesSatisfiableModulesIndependently() throws Exception {
        Path complete = Files.createDirectories(temporaryDirectory.resolve("com.example.complete"));
        write(complete.resolve("module-info.java"), "module com.example.complete {}");
        Path completeSource = write(complete.resolve("example/Complete.java"), "package example;class Complete{java.time.Duration duration;}");
        Path incomplete = Files.createDirectories(temporaryDirectory.resolve("com.example.incomplete"));
        write(incomplete.resolve("module-info.java"), "module com.example.incomplete { requires com.example.missing; }");
        Path incompleteSource = write(incomplete.resolve("example/Incomplete.java"), "package example;class Incomplete{java.time.Duration duration;}");

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString(), "--module", "com.example.complete,com.example.incomplete");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                package example;

                import java.time.Duration;

                class Complete {
                    Duration duration;
                }
                """,
                Files.readString(completeSource));
        assertEquals(
                """
                package example;

                class Incomplete {
                    java.time.Duration duration;
                }
                """,
                Files.readString(incompleteSource));
    }

    @Test
    void retainsQualifiedReferencesThatWouldBeAmbiguous() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "class Example{java.util.Date external;Date local;}class Date{}");

        Invocation invocation = run("", source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                class Example {
                    java.util.Date external;
                    Date local;
                }

                class Date {}
                """,
                Files.readString(source));
    }

    @Test
    void retainsQualifiedReferencesThatConflictWithTypesInTheSamePackage() throws Exception {
        Path conflict = write(temporaryDirectory.resolve("example/Date.java"), "package example;class Date{}");
        Path source = write(temporaryDirectory.resolve("example/Example.java"), "package example;class Example{java.util.Date external;}");

        Invocation invocation = run("", conflict.toString(), source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                package example;

                class Example {
                    java.util.Date external;
                }
                """,
                Files.readString(source));
    }

    @Test
    void retainsNestedTypesDeclaredInTheSameSource() throws Exception {
        Path source = write(temporaryDirectory.resolve("example/Example.java"), "package example;class Example{static class Nested{}Example.Nested value;}");

        Invocation invocation = run("", source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                package example;

                class Example {
                    static class Nested {}

                    Example.Nested value;
                }
                """,
                Files.readString(source));
    }

    @Test
    void retainsQualifiedReferencesThatConflictWithExplicitImports() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "import java.sql.Date;class Example{java.util.Date external;Date local;}");

        Invocation invocation = run("", source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                import java.sql.Date;

                class Example {
                    java.util.Date external;
                    Date local;
                }
                """,
                Files.readString(source));
    }

    @Test
    void retainsQualifiedReferencesShadowedInExpressionScope() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "class Example{void example(){int Duration=0;Object zero=java.time.Duration.ZERO;}} ");

        Invocation invocation = run("", source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                class Example {
                    void example() {
                        int Duration = 0;
                        Object zero = java.time.Duration.ZERO;
                    }
                }
                """,
                Files.readString(source));
    }

    @Test
    void retainsNestedReferencesThatCannotBeImported() throws Exception {
        Path source = write(temporaryDirectory.resolve("Example.java"), "class Example{Other.Nested nested;}class Other{static class Nested{}}");

        Invocation invocation = run("", source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(
                """
                class Example {
                    Other.Nested nested;
                }

                class Other {
                    static class Nested {}
                }
                """,
                Files.readString(source));
    }

    @Test
    void formatsModuleSourcePathByDefault() throws Exception {
        Path first = module("com.example.first", "First");
        Path second = module("com.example.second", "Second");
        Path nonModule = write(temporaryDirectory.resolve("other/example/Other.java"), unformattedModuleClass("Other"));

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedModuleClass("First"), Files.readString(first));
        assertEquals(formattedModuleClass("Second"), Files.readString(second));
        assertEquals(unformattedModuleClass("Other"), Files.readString(nonModule));
    }

    @Test
    void formatsOnlyExplicitFilesWithModuleSourcePath() throws Exception {
        Path first = module("com.example.first", "First");
        Path sibling = write(first.resolveSibling("Sibling.java"), unformattedModuleClass("Sibling"));
        Path second = module("com.example.second", "Second");
        Path descriptor = first.getParent().getParent().resolve("module-info.java");
        String originalDescriptor = Files.readString(descriptor);

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString(), first.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedModuleClass("First"), Files.readString(first));
        assertEquals(unformattedModuleClass("Sibling"), Files.readString(sibling));
        assertEquals(unformattedModuleClass("Second"), Files.readString(second));
        assertEquals(originalDescriptor, Files.readString(descriptor));
    }

    @Test
    void checksSelectedModuleAlongsideExplicitFiles() throws Exception {
        Path first = module("com.example.first", "First");
        Path sibling = write(first.resolveSibling("Sibling.java"), unformattedModuleClass("Sibling"));

        Invocation invocation = run("", "--check", "--module-source-path", temporaryDirectory.toString(), "--module", "com.example.first", first.toString());

        assertEquals(1, invocation.exitCode());
        assertEquals(Set.of(first + ": not formatted", sibling.toRealPath() + ": not formatted"),
                Set.copyOf(invocation.error().lines().toList()));
        assertEquals(unformattedModuleClass("First"), Files.readString(first));
        assertEquals(unformattedModuleClass("Sibling"), Files.readString(sibling));
    }

    @Test
    void formatsSelectedModuleAndExplicitFileInAnotherModule() throws Exception {
        Path first = module("com.example.first", "First");
        Path sibling = write(first.resolveSibling("Sibling.java"), unformattedModuleClass("Sibling"));
        Path second = module("com.example.second", "Second");
        Path unrelated = write(second.resolveSibling("Unrelated.java"), unformattedModuleClass("Unrelated"));

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString(), "--module", "com.example.first", second.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedModuleClass("First"), Files.readString(first));
        assertEquals(formattedModuleClass("Sibling"), Files.readString(sibling));
        assertEquals(formattedModuleClass("Second"), Files.readString(second));
        assertEquals(unformattedModuleClass("Unrelated"), Files.readString(unrelated));
    }

    @Test
    void resolvesModuleSourceDependenciesWithoutFormattingThem() throws Exception {
        Path dependencyRoot = temporaryDirectory.resolve("com.example.dependency");
        Path dependencyDescriptor = write(dependencyRoot.resolve("module-info.java"), "module com.example.dependency { exports dependency; }");
        String dependencyInput = "package dependency;public class Widget{}";
        Path dependency = write(dependencyRoot.resolve("dependency/Widget.java"), dependencyInput);
        Path applicationRoot = temporaryDirectory.resolve("com.example.application");
        write(applicationRoot.resolve("module-info.java"), "module com.example.application { requires com.example.dependency; }");
        Path source = write(applicationRoot.resolve("application/Example.java"), "package application;class Example{dependency.Widget widget;}");

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString(), "--module", "com.example.application", source.toString());

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedWidgetUser(), Files.readString(source));
        assertEquals(dependencyInput, Files.readString(dependency));
        assertEquals("module com.example.dependency { exports dependency; }", Files.readString(dependencyDescriptor));
    }

    @Test
    void formatsMultipleRootsForOneModule() throws Exception {
        Path first = module("com.example.first", "First");
        Path generatedRoot = temporaryDirectory.resolve("generated");
        Path generated = write(generatedRoot.resolve("example/Generated.java"), unformattedModuleClass("Generated"));

        Invocation invocation = run("", "--module-source-path", "com.example.first=" + first.getParent().getParent() + File.pathSeparator + generatedRoot,
                "--module", "com.example.first");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedModuleClass("First"), Files.readString(first));
        assertEquals(formattedModuleClass("Generated"), Files.readString(generated));
    }

    @Test
    void formatsModuleSourcePathList() throws Exception {
        Path first = module("com.example.first", "First");
        Path otherRoot = temporaryDirectory.resolve("other");
        Path secondRoot = otherRoot.resolve("com.example.second");
        write(secondRoot.resolve("module-info.java"), "module com.example.second {}\n");
        Path second = write(secondRoot.resolve("example/Second.java"), unformattedModuleClass("Second"));

        Invocation invocation = run("", "--module-source-path", temporaryDirectory + File.pathSeparator + otherRoot,
                "--module", "com.example.first,com.example.second");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedModuleClass("First"), Files.readString(first));
        assertEquals(formattedModuleClass("Second"), Files.readString(second));
    }

    @Test
    void formatsModuleWithMultipleSourcePatterns() throws Exception {
        Path mainRoot = temporaryDirectory.resolve("com.example.first/src/main/java");
        write(mainRoot.resolve("module-info.java"), "module com.example.first {}\n");
        Path first = write(mainRoot.resolve("example/First.java"), unformattedModuleClass("First"));
        Path generatedRoot = temporaryDirectory.resolve("com.example.first/generated");
        Path generated = write(generatedRoot.resolve("example/Generated.java"), unformattedModuleClass("Generated"));

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.resolve("*/src/main/java") + File.pathSeparator
                + temporaryDirectory.resolve("*/generated"), "--module", "com.example.first");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedModuleClass("First"), Files.readString(first));
        assertEquals(formattedModuleClass("Generated"), Files.readString(generated));
    }

    @Test
    void rejectsMissingModuleWithExplicitFile() throws Exception {
        Path source = module("com.example.first", "First");

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString(), "--module", "com.example.missing", source.toString());

        assertEquals(1, invocation.exitCode());
        assertEquals("module com.example.missing not found in module source path\n", invocation.error());
        assertEquals(unformattedModuleClass("First"), Files.readString(source));
    }

    @Test
    void formatsConventionModuleSourcePath() throws Exception {
        Path first = module("com.example.first", "First");
        Path second = module("com.example.second", "Second");

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString(), "--module", "com.example.first,com.example.second");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedModuleClass("First"), Files.readString(first));
        assertEquals(formattedModuleClass("Second"), Files.readString(second));
    }

    @Test
    void formatsOnlySelectedModule() throws Exception {
        Path first = module("com.example.first", "First");
        Path second = module("com.example.second", "Second");

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString(), "--module", "com.example.first");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedModuleClass("First"), Files.readString(first));
        assertEquals(unformattedModuleClass("Second"), Files.readString(second));
    }

    @Test
    void formatsModuleSpecificSourcePath() throws Exception {
        Path module = Files.createDirectories(temporaryDirectory.resolve("specific"));
        write(module.resolve("module-info.java"), "module com.example.specific {}\n");
        Path source = write(module.resolve("example/Specific.java"), unformattedModuleClass("Specific"));

        Invocation invocation = run("", "--module-source-path", "com.example.specific=" + module, "--module", "com.example.specific");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedModuleClass("Specific"), Files.readString(source));
    }

    @Test
    void selectsModuleSpecificSourcePathWithShortOption() throws Exception {
        Path first = module("com.example.first", "First");
        Path second = module("com.example.second", "Second");

        Invocation invocation = run("", "--module-source-path", "com.example.first=" + first.getParent().getParent(), "--module-source-path",
                "com.example.second=" + second.getParent().getParent(), "-m", "com.example.second");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(unformattedModuleClass("First"), Files.readString(first));
        assertEquals(formattedModuleClass("Second"), Files.readString(second));
    }

    @Test
    void selectsCommaSeparatedModules() throws Exception {
        Path first = module("com.example.first", "First");
        Path second = module("com.example.second", "Second");
        Path third = module("com.example.third", "Third");

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString(), "--module=com.example.first,com.example.third");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedModuleClass("First"), Files.readString(first));
        assertEquals(unformattedModuleClass("Second"), Files.readString(second));
        assertEquals(formattedModuleClass("Third"), Files.readString(third));
    }

    @Test
    void repeatedModuleSelectionReplacesPreviousSelection() throws Exception {
        Path first = module("com.example.first", "First");
        Path second = module("com.example.second", "Second");

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString(), "-m", "com.example.first",
                "--module", "com.example.second");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(unformattedModuleClass("First"), Files.readString(first));
        assertEquals(formattedModuleClass("Second"), Files.readString(second));
    }

    @Test
    void rejectsModuleMissingFromSourcePath() throws Exception {
        module("com.example.first", "First");

        Invocation invocation = run("", "--module-source-path", temporaryDirectory.toString(), "-m", "com.example.missing");

        assertEquals(1, invocation.exitCode());
        assertEquals("module com.example.missing not found in module source path\n", invocation.error());
    }

    @Test
    void rejectsModuleSelectionWithoutModuleSourcePath() {
        Invocation invocation = run("", "--module", "com.example.first");

        assertEquals(1, invocation.exitCode());
        assertEquals("module source path must be specified if --module is used\n", invocation.error());
    }

    @Test
    void formatsModulePattern() throws Exception {
        Path sourceRoot = temporaryDirectory.resolve("com.example.pattern/src/main/java");
        write(sourceRoot.resolve("module-info.java"), "module com.example.pattern {}\n");
        Path source = write(sourceRoot.resolve("example/Pattern.java"), unformattedModuleClass("Pattern"));

        Invocation invocation = run("", "--module-source-path",
                temporaryDirectory.resolve("*/src/main/java").toString(), "--module", "com.example.pattern");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(formattedModuleClass("Pattern"), Files.readString(source));
    }

    @Test
    void selectsModuleFromPattern() throws Exception {
        Path firstRoot = temporaryDirectory.resolve("com.example.first/src/main/java");
        write(firstRoot.resolve("module-info.java"), "module com.example.first {}\n");
        Path first = write(firstRoot.resolve("example/First.java"), unformattedModuleClass("First"));
        Path secondRoot = temporaryDirectory.resolve("com.example.second/src/main/java");
        write(secondRoot.resolve("module-info.java"), "module com.example.second {}\n");
        Path second = write(secondRoot.resolve("example/Second.java"), unformattedModuleClass("Second"));

        Invocation invocation = run(
                "",
                "--module-source-path",
                temporaryDirectory.resolve("*/src/main/java").toString(),
                "-m",
                "com.example.second");

        assertEquals(0, invocation.exitCode(), invocation.error());
        assertEquals(unformattedModuleClass("First"), Files.readString(first));
        assertEquals(formattedModuleClass("Second"), Files.readString(second));
    }

    private Path sourceArchive() throws Exception {
        Path archive = temporaryDirectory.resolve("dependency-sources.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new JarEntry("dependency/Widget.java"));
            output.write("package dependency;public class Widget{}".getBytes(UTF_8));
            output.closeEntry();
        }
        return archive;
    }

    private Path module(String moduleName, String className) throws Exception {
        Path module = Files.createDirectories(temporaryDirectory.resolve(moduleName));
        write(module.resolve("module-info.java"), "module " + moduleName + " {}\n");
        return write(module.resolve("example/" + className + ".java"), unformattedModuleClass(className));
    }

    private static String formattedWithoutImportNormalization() {
        return """
                package example;

                import java.util.*;

                class Example {
                    java.time.Duration duration;
                    List<String> values;
                }
                """;
    }

    private static String formattedWidgetUser() {
        return """
                package application;

                import dependency.Widget;

                class Example {
                    Widget widget;
                }
                """;
    }

    private static String unformattedModuleClass(String className) {
        return "package example;class " + className + "{int value;}";
    }

    private static String formattedModuleClass(String className) {
        return "package example;\n\nclass " + className + " {\n    int value;\n}\n";
    }

    private static Path write(Path path, String contents) throws Exception {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, contents, UTF_8);
    }

    private static Invocation run(String input, String... arguments) {
        Tool tool = tool();
        var output = new ByteArrayOutputStream();
        var error = new ByteArrayOutputStream();
        int exitCode = tool.run(new ByteArrayInputStream(input.getBytes(UTF_8)), output, error, arguments);
        return new Invocation(exitCode, output.toString(UTF_8), error.toString(UTF_8));
    }

    private static ModuleFinder finder(ModuleReference... references) {
        var modules = Set.of(references);
        return new ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(String name) {
                return modules.stream()
                        .filter(reference -> reference.descriptor().name().equals(name))
                        .findFirst();
            }

            @Override
            public Set<ModuleReference> findAll() {
                return modules;
            }
        };
    }

    private static Tool tool() {
        return ServiceLoader.load(Tool.class).stream()
                .map(Provider::get)
                .filter(candidate -> candidate.name().equals("jfmt"))
                .findFirst()
                .orElseThrow();
    }

    private static java.util.spi.ToolProvider toolProvider() {
        return ServiceLoader.load(java.util.spi.ToolProvider.class).stream()
                .map(Provider::get)
                .filter(candidate -> candidate.name().equals("jfmt"))
                .findFirst()
                .orElseThrow();
    }

    private static void assertOptionContract(Object tool) {
        OptionChecker options = assertInstanceOf(OptionChecker.class, tool);

        assertEquals(1, options.isSupportedOption("--class-path"));
        assertEquals(1, options.isSupportedOption("-classpath"));
        assertEquals(1, options.isSupportedOption("-cp"));
        assertEquals(1, options.isSupportedOption("--module-path"));
        assertEquals(1, options.isSupportedOption("-p"));
        assertEquals(1, options.isSupportedOption("--upgrade-module-path"));
        assertEquals(1, options.isSupportedOption("--source-path"));
        assertEquals(1, options.isSupportedOption("-sourcepath"));
        assertEquals(1, options.isSupportedOption("--module-source-path"));
        assertEquals(-1, options.isSupportedOption("--module-source-path=src"));
        assertEquals(1, options.isSupportedOption("--module"));
        assertEquals(-1, options.isSupportedOption("--module=com.example.application"));
        assertEquals(1, options.isSupportedOption("-m"));
        assertEquals(1, options.isSupportedOption("--add-modules"));
        assertEquals(1, options.isSupportedOption("--limit-modules"));
        assertEquals(1, options.isSupportedOption("--add-exports"));
        assertEquals(1, options.isSupportedOption("--add-reads"));
        assertEquals(1, options.isSupportedOption("--patch-module"));
        assertEquals(1, options.isSupportedOption("--release"));
        assertEquals(1, options.isSupportedOption("--source"));
        assertEquals(1, options.isSupportedOption("-source"));
        assertEquals(0, options.isSupportedOption("--enable-preview"));
        assertEquals(0, options.isSupportedOption("--check"));
        assertEquals(0, options.isSupportedOption("--help"));
        assertEquals(0, options.isSupportedOption("--version"));
        assertEquals(0, options.isSupportedOption("__complete"));
        assertEquals(0, options.isSupportedOption("--aot-warmup"));
        assertEquals(-1, options.isSupportedOption("--preserve-author-breaks"));
        assertEquals(-1, options.isSupportedOption("--unknown"));
    }

    private record Invocation(int exitCode, String output, String error) {}
}
