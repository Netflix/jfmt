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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import com.netflix.tools.jfmt.CommandLine.Cardinality;
import com.netflix.tools.jfmt.CommandLine.ParsedArguments;
import com.netflix.tools.jfmt.CommandLine.ToolOption;

final class JfmtCommandLine {
    private static final JfmtCommandLine INSTANCE = new JfmtCommandLine();

    private final ToolOption classPath = option("--class-path", "PATH", "Where to find user class files", "-classpath", "-cp");
    private final ToolOption modulePath = option("--module-path", "PATH", "Where to find application modules", "-p");
    private final ToolOption upgradeModulePath = option("--upgrade-module-path", "PATH", "Where to find upgradeable modules");
    private final ToolOption sourcePath = option("--source-path", "PATH", "Where to find input source files", "-sourcepath");
    private final ToolOption moduleSourcePath = option("--module-source-path", "PATH", "Where to find module source trees");
    private final ToolOption system = option("--system", "JDK|none", "Override the system modules location");
    private final ToolOption module = option("--module", "MODULE[,MODULE...]", "Select source modules", "-m");
    private final ToolOption addModules = option("--add-modules", "MODULE[,MODULE...]", "Root modules in addition to the initial modules");
    private final ToolOption limitModules = option("--limit-modules", "MODULE[,MODULE...]", "Limit the observable modules");
    private final ToolOption addExports = option("--add-exports", "MODULE/PACKAGE=TARGET", "Add an exported package");
    private final ToolOption addReads = option("--add-reads", "MODULE=TARGET", "Add a module readability edge");
    private final ToolOption patchModule = option("--patch-module", "MODULE=PATH", "Patch a module with classes and resources");
    private final ToolOption release = option("--release", "RELEASE", "Compile for the specified Java SE release");
    private final ToolOption source = option("--source", "RELEASE", "Use the specified source release", "-source");
    private final ToolOption enablePreview = flag("--enable-preview", "Enable preview language features");
    private final ToolOption check = flag("--check", "Check formatting without changing files");
    private final ToolOption help = flag("--help", "Print this help message", "-h", "-?");
    private final CommandLine commandLine = CommandLine.builder()
            .description("Format Java source")
            .options(
                    classPath,
                    modulePath,
                    upgradeModulePath,
                    sourcePath,
                    moduleSourcePath,
                    system,
                    module,
                    addModules,
                    limitModules,
                    addExports,
                    addReads,
                    patchModule,
                    release,
                    source,
                    enablePreview,
                    check,
                    help)
            .operand("FILE", "Java source file, or - for standard input", Cardinality.ZERO_OR_MORE)
            .argumentFiles()
            .completion()
            .build();

    private JfmtCommandLine() {}

    static JfmtCommandLine instance() {
        return INSTANCE;
    }

    int isSupportedOption(String option) {
        return "--aot-warmup".equals(option) ? 0 : commandLine.isSupportedOption(option);
    }

    OptionalInt runCompletion(PrintWriter out, PrintWriter err, String... arguments) {
        return commandLine.runCompletion(out, err, arguments);
    }

    String help() {
        return commandLine.help("jfmt");
    }

    Options parse(String... arguments) {
        ParsedArguments parsed = commandLine.parse(arguments);
        List<String> modules = parsed.values(module);
        String selectedModules = modules.isEmpty() ? null : modules.getLast();
        List<String> compilerOptions = new ArrayList<>();
        addLast(compilerOptions, "--class-path", parsed.values(classPath));
        addLast(compilerOptions, "--module-path", parsed.values(modulePath));
        addLast(compilerOptions, "--upgrade-module-path", parsed.values(upgradeModulePath));
        addLast(compilerOptions, "--source-path", parsed.values(sourcePath));
        addEach(compilerOptions, "--module-source-path", parsed.values(moduleSourcePath));
        addLast(compilerOptions, "--system", parsed.values(system));
        if (selectedModules != null) {
            compilerOptions.add("--module");
            compilerOptions.add(selectedModules);
        }
        addLast(compilerOptions, "--add-modules", parsed.values(addModules));
        addLast(compilerOptions, "--limit-modules", parsed.values(limitModules));
        addEach(compilerOptions, "--add-exports", parsed.values(addExports));
        addEach(compilerOptions, "--add-reads", parsed.values(addReads));
        addEach(compilerOptions, "--patch-module", parsed.values(patchModule));
        addLast(compilerOptions, "--release", parsed.values(release));
        addLast(compilerOptions, "--source", parsed.values(source));
        if (parsed.contains(enablePreview)) {
            compilerOptions.add("--enable-preview");
        }
        return new Options(parsed.contains(check), parsed.values(moduleSourcePath), selectedModules,
                List.copyOf(compilerOptions), parsed.operands(), parsed.contains(help));
    }

    record Options(boolean check, List<String> moduleSourcePaths, String modules,
                   List<String> compilerOptions, List<String> files, boolean help) {}

    private static void addLast(List<String> result, String option, List<String> values) {
        if (!values.isEmpty()) {
            result.add(option);
            result.add(values.getLast());
        }
    }

    private static void addEach(List<String> result, String option, List<String> values) {
        for (String value : values) {
            result.add(option);
            result.add(value);
        }
    }

    private static ToolOption flag(String name, String description, String... aliases) {
        return ToolOption.flag(name, description, aliases);
    }

    private static ToolOption option(String name, String argument, String description,
            String... aliases) {
        return ToolOption.option(name, argument, description, aliases);
    }
}
