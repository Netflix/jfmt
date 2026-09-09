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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import javax.tools.OptionChecker;

import com.netflix.tools.jfmt.CommandLine.ConfigurationException;
import com.netflix.tools.jfmt.ImportNormalizer.AttributionException;
import com.netflix.tools.jfmt.JfmtCommandLine.Options;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.FormatterException;

/**
 * {@link ToolProvider} entry point for jfmt.
 *
 * <ul>
 *   <li>{@code jfmt <files>} -- format files in place
 *   <li>{@code jfmt --module-source-path <path> --module <module>} -- format
 *       selected source modules
 *   <li>{@code jfmt -} -- read stdin, write stdout
 *   <li>{@code jfmt --check <files>} -- exit non-zero if any file is
 *       unformatted
 * </ul>
 */
public class JfmtToolProvider implements ToolProvider, OptionChecker {
    private static final JfmtCommandLine COMMAND_LINE = JfmtCommandLine.instance();

    @Override
    public String name() {
        return "jfmt";
    }

    @Override
    public int isSupportedOption(String option) {
        return COMMAND_LINE.isSupportedOption(option);
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String... args) {
        return run(System.in, out, err, args);
    }

    int run(InputStream in, PrintWriter out, PrintWriter err,
            String... args) {
        var version = COMMAND_LINE.runVersion(out, args);
        if (version.isPresent()) {
            return version.orElseThrow();
        }
        var completion = COMMAND_LINE.runCompletion(out, err, args);
        if (completion.isPresent()) {
            return completion.orElseThrow();
        }
        if (args.length == 1 && "--aot-warmup".equals(args[0])) {
            return warmup(err);
        }

        Options options;
        try {
            options = COMMAND_LINE.parse(args);
        } catch (ConfigurationException | IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        }
        if (options.help()) {
            out.print(COMMAND_LINE.help());
            out.flush();
            return 0;
        }
        if (args.length == 0) {
            printUsage(err);
            return 1;
        }

        List<String> moduleSourcePaths = options.moduleSourcePaths();
        Set<String> selectedModules = options.modules() == null ? null : moduleNames(options.modules());
        if (selectedModules != null && moduleSourcePaths.isEmpty()) {
            err.println("module source path must be specified if --module is used");
            return 1;
        }

        JfmtFormatter formatter = new JfmtFormatter();

        if (moduleSourcePaths.isEmpty() && options.files().size() == 1 && "-".equals(options.files()
                .getFirst())) {
            return formatStdin(formatter, in, out, err);
        }

        Set<Path> files = new LinkedHashSet<>();
        options.files().stream()
                .map(Path::of)
                .map(Path::normalize)
                .forEach(files::add);
        if (!collectSources(moduleSourcePaths, selectedModules, files, err)) {
            return 1;
        }

        if (files.isEmpty()) {
            err.println("no Java source files were found");
            return 1;
        }

        List<Path> sortedFiles = files.stream()
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        Map<Path, String> sources = new LinkedHashMap<>();
        boolean readErrors = false;
        for (Path path : sortedFiles) {
            try {
                sources.put(path, Files.readString(path, StandardCharsets.UTF_8));
            } catch (IOException e) {
                err.println(path + ": " + e.getMessage());
                readErrors = true;
            }
        }
        if (sources.isEmpty()) {
            return 1;
        }
        Map<Path, String> normalized;
        try {
            normalized = ImportNormalizer.normalize(sources, options.compilerOptions());
        } catch (AttributionException e) {
            err.println(e.getMessage());
            return 1;
        }
        int result;
        if (sources.size() == 1) {
            Path path = sources.keySet()
                               .iterator()
                               .next();
            result = formatFile(formatter, path, sources.get(path), normalized.get(path),
                    options.check(), err);
        } else {
            result = formatFiles(formatter, sources, normalized, options.check(), err);
        }
        return readErrors ? 1 : result;
    }

    private static void printUsage(PrintWriter err) {
        err.println("Usage: jfmt [--check] <files...>");
        err.println("       jfmt [--check] --module-source-path <path> [-m <module>]");
        err.println("       jfmt -");
    }

    private static Set<String> moduleNames(String value) {
        Set<String> modules = new LinkedHashSet<>();
        for (String module : value.split(",", -1)) {
            modules.add(module);
        }
        return modules;
    }

    private boolean collectSources(List<String> pathLists, Set<String> selectedModules, Set<Path> files,
            PrintWriter err) {
        Set<String> foundModules = new LinkedHashSet<>();
        for (String pathList : pathLists) {
            try {
                collectModuleSourcePath(pathList, selectedModules, foundModules, files);
            } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
                err.println(pathList + ": " + e.getMessage());
                return false;
            }
        }
        if (selectedModules != null) {
            for (String module : selectedModules) {
                if (!foundModules.contains(module)) {
                    err.println("module " + module + " not found in module source path");
                    return false;
                }
            }
        }
        return true;
    }

    private void collectModuleSourcePath(String value, Set<String> selectedModules, Set<String> foundModules,
            Set<Path> files)
            throws IOException {
        int equals = value.indexOf('=');
        if (equals > 0) {
            if (equals == value.length() - 1) {
                throw new IllegalArgumentException("module-specific path is empty");
            }
            String moduleName = value.substring(0, equals);
            Path root = requireModuleDirectory(Path.of(value.substring(equals + 1)));
            if (selectedModules == null || selectedModules.contains(moduleName)) {
                foundModules.add(moduleName);
                collectJavaFiles(root, files);
            }
            return;
        }

        int wildcard = value.indexOf('*');
        if (wildcard >= 0) {
            if (value.indexOf('*', wildcard + 1) >= 0) {
                throw new IllegalArgumentException("module source path contains more than one wildcard");
            }
            collectModulePattern(value, wildcard, selectedModules, foundModules, files);
            return;
        }

        Path root = requireDirectory(Path.of(value));
        if (Files.isRegularFile(root.resolve("module-info.java"))) {
            Path fileName = root.getFileName();
            String moduleName = fileName == null ? "" : fileName.toString();
            if (selectedModules == null || selectedModules.contains(moduleName)) {
                foundModules.add(moduleName);
                collectJavaFiles(root, files);
            }
            return;
        }
        try (Stream<Path> children = Files.list(root)) {
            for (Path module : children.filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("module-info.java")))
                    .toList()) {
                String moduleName = module.getFileName().toString();
                if (selectedModules == null || selectedModules.contains(moduleName)) {
                    foundModules.add(moduleName);
                    collectJavaFiles(module, files);
                }
            }
        }
    }

    private void collectModulePattern(String value, int wildcard, Set<String> selectedModules,
            Set<String> foundModules, Set<Path> files)
            throws IOException {
        String prefix = value.substring(0, wildcard);
        String suffix = value.substring(wildcard + 1);
        while (suffix.startsWith("/") || suffix.startsWith("\\")) {
            suffix = suffix.substring(1);
        }
        Path root = requireDirectory(Path.of(prefix.isEmpty() ? "." : prefix));
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.filter(Files::isDirectory).toList()) {
                String moduleName = child.getFileName().toString();
                if (selectedModules != null && !selectedModules.contains(moduleName)) {
                    continue;
                }
                Path module = suffix.isEmpty() ? child : child.resolve(suffix);
                if (Files.isRegularFile(module.resolve("module-info.java"))) {
                    foundModules.add(moduleName);
                    collectJavaFiles(module, files);
                }
            }
        }
    }

    private Path requireModuleDirectory(Path path) {
        Path root = requireDirectory(path);
        if (!Files.isRegularFile(root.resolve("module-info.java"))) {
            throw new IllegalArgumentException("module-info.java not found in " + root);
        }
        return root;
    }

    private Path requireDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("not a directory: " + path);
        }
        return path;
    }

    private void collectJavaFiles(Path root, Set<Path> files) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.getFileName()
                                     .toString()
                                     .endsWith(".java"))
                 .map(Path::normalize)
                 .forEach(files::add);
        }
    }

    private int formatFile(JfmtFormatter formatter, Path path, String input,
                           String normalized, boolean check, PrintWriter err) {
        try {
            String output = formatter.formatSource(normalized);
            if (check) {
                if (!input.equals(output)) {
                    err.println(path + ": not formatted");
                    return 1;
                }
            } else if (!input.equals(output)) {
                Files.writeString(path, output, StandardCharsets.UTF_8);
            }
            return 0;
        } catch (FormatterException | IOException e) {
            err.println(path + ": " + e.getMessage());
            return 1;
        }
    }

    private int formatFiles(JfmtFormatter formatter, Map<Path, String> sources, Map<Path, String> normalized,
                            boolean check, PrintWriter err) {
        var unformatted = new AtomicBoolean();
        var errors = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(Runtime.getRuntime()
                .availableProcessors())) {
            List<Future<?>> futures = new ArrayList<>(sources.size());
            for (var source : sources.entrySet()) {
                Path path = source.getKey();
                String input = source.getValue();
                futures.add(executor.submit(() -> {
                    try {
                        String output = formatter.formatSource(normalized.get(path));
                        if (check) {
                            if (!input.equals(output)) {
                                synchronized (err) {
                                    err.println(path + ": not formatted");
                                }
                                unformatted.set(true);
                            }
                        } else if (!input.equals(output)) {
                            Files.writeString(path, output, StandardCharsets.UTF_8);
                        }
                    } catch (FormatterException | IOException e) {
                        synchronized (err) {
                            err.println(path + ": " + e.getMessage());
                        }
                        errors.incrementAndGet();
                    }
                }));
            }
            for (var future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return 1;
                } catch (ExecutionException e) {
                    throw new RuntimeException(e.getCause());
                }
            }
        }

        if (errors.get() > 0) {
            return 1;
        }
        return check && unformatted.get() ? 1 : 0;
    }

    /**
     * Exercise the formatter's hot paths so the AOT cache captures profiled code.
     * Called by the native launcher on the first run when no cache exists.
     * Formats representative sources with enough iterations for the JIT to
     * collect useful profile data (C1 compile at ~200 invocations, profile
     * maturity well before Tier4's 5000 invocation threshold).
     */
    private int warmup(PrintWriter err) {
        var formatter = new JfmtFormatter();
        // Deliberately ugly source that exercises the formatter's hard paths:
        // long lines that need breaking, verbose javadoc that needs reflowing,
        // deeply nested generics, long method chains, complex annotations,
        // mixed indentation, and varied control flow.
        String source =
                """
            package com.example;

            import java.util.function.Function;
            import java.util.Map;
            import java.io.IOException;
            import java.util.stream.Collectors;
            import java.util.List;
            import java.util.Optional;
            import java.util.concurrent.CompletableFuture;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.ArrayList;
            import java.util.Objects;

            /**
             * This class has verbose javadoc that the formatter must reflow into
             * properly wrapped lines with correct paragraph handling.
             *
             *    <p>This paragraph has leading whitespace and a very long first sentence that should be reflowed because it exceeds any reasonable column limit and tests the javadoc line-breaking logic.
             *
             * <p>Here is a list:
             * <ul>
             *   <li>First item with {@code inline code} and {@link Map#of(Object, Object)} reference that makes the line quite long
             *   <li>Second item with a <a href="https://example.com/very/long/url/that/pushes/past/the/column/limit">link</a>
             *   <li>Third item
             * </ul>
             *
             * <pre>{@code
             * var x = new Warmup<>(Map.of("key", List.of("a", "b", "c")));
             * x.processAll(Function.identity(), Collectors.toList());
             * }</pre>
             *
             * @param <K> the key type which may be any comparable type including but not limited to String, Integer, or custom implementations
             * @param <V> the value type
             * @throws IOException when the underlying storage layer encounters an unrecoverable I/O error during initialization
             * @throws IllegalArgumentException when any of the provided arguments fail validation checks
             * @see ConcurrentHashMap
             * @since 2.0
             * @deprecated Use {@link #processAll(Function, java.util.stream.Collector)} instead of the legacy entry point which does not support concurrent access patterns
             */
                @SuppressWarnings({"unchecked", "rawtypes", "deprecation"}) @Deprecated(since = "2.0", forRemoval = true)
            public sealed class Warmup<K extends Comparable<? super K>, V extends CharSequence & java.io.Serializable> permits Warmup.Impl, Warmup.Stub {
                private final ConcurrentHashMap<K, List<V>> data;private final Function<? super V, ? extends CharSequence> transformer;

              // long lines that the formatter must break
              public Warmup(ConcurrentHashMap<K, List<V>> data, Function<? super V, ? extends CharSequence> transformer, boolean validate, Optional<String> name) throws IOException, IllegalArgumentException, ReflectiveOperationException {
                        this.data = Objects.requireNonNull(data, "data must not be null"); this.transformer = Objects.requireNonNull(transformer, "transformer must not be null");
                    if (validate && data.isEmpty()) { throw new IllegalArgumentException("data must not be empty when validation is enabled, got size=" + data.size()); }
                }

                // deeply nested generics and long chains
                public Map<K, List<String>> processAll(Function<? super V, ? extends String> mapper, java.util.stream.Collector<? super String, ?, List<String>> collector) {
                    return data.entrySet().stream().filter(e -> e.getValue() != null && !e.getValue().isEmpty()).collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().map(mapper).filter(Objects::nonNull).map(s -> s.strip().toLowerCase()).collect(collector), (a, b) -> { a.addAll(b); return a; }, ArrayList::new));
                }

                /**
                 * @param key the lookup key
                 * @return the first matching value, or empty
                 */
                public Optional<V> first(K key) {
                    return Optional.ofNullable(data.get(key)).flatMap(list -> list.stream().filter(v -> v != null && v.length() > 0).findFirst());
                }

                // switch expression, ternary, complex control flow
                public String classify(K key, V fallback) {
                    var values = data.getOrDefault(key, List.of());
                    return switch (values.size()) {
                        case 0 -> fallback != null ? "fallback:" + transformer.apply(fallback) : "empty";
                        case 1 -> "single:" + values.getFirst();
                        case int n when n > 100 -> "large(" + n + "):" + values.stream().map(transformer).map(CharSequence::toString).collect(Collectors.joining(","));
                        default -> {
                            var summary = values.stream().map(v -> transformer.apply(v).toString()).sorted().collect(Collectors.joining(", ", "[", "]"));
                            yield "multi(" + values.size() + "):" + summary;
                        }
                    };
                }

                // try-with-resources, lambdas, nested blocks
                public CompletableFuture<Map<K, String>> asyncSummarize(java.util.concurrent.Executor executor) {
                    return CompletableFuture.supplyAsync(() -> {
                        try (var scope = new Object() { void close() {} }) {
                            return data.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                                return entry.getValue().stream().map(v -> { var transformed = transformer.apply(v); return transformed != null ? transformed.toString() : "null"; }).collect(Collectors.joining("; "));
                            }));
                        }
                    }, executor);
                }

                // long string concatenation
                @Override public String toString() { return "Warmup{" + "dataSize=" + data.size() + ", keys=" + data.keySet() + ", transformer=" + transformer + ", hashCode=" + hashCode() + ", identity=" + System.identityHashCode(this) + "}"; }

                /** Record with compact constructor and long field types. */
                record Entry<KK extends Comparable<? super KK>, VV>(KK key, List<VV> values, Function<? super VV, String> formatter, Optional<String> label) {
                    Entry { Objects.requireNonNull(key); Objects.requireNonNull(values); values = List.copyOf(values); if (label != null) { label = label.filter(s -> !s.isBlank()); } }
                    String format() { return label.orElse(key.toString()) + "=" + values.stream().map(formatter).collect(Collectors.joining(",")); }
                }

                // enum with methods
                enum Strategy { EAGER { @Override public <T> List<T> apply(List<T> input) { return List.copyOf(input); } }, LAZY { @Override public <T> List<T> apply(List<T> input) { return input; } }; public abstract <T> List<T> apply(List<T> input); }

                static final class Impl extends Warmup<String, String> { Impl(ConcurrentHashMap<String, List<String>> data) throws IOException, ReflectiveOperationException { super(data, Function.identity(), true, Optional.empty()); } }
                static non-sealed class Stub extends Warmup<String, String> { Stub() throws IOException, ReflectiveOperationException { super(new ConcurrentHashMap<>(), Function.identity(), false, Optional.of("stub")); } }
            }
            """;
        try {
            for (int i = 0; i < 1000; i++) {
                formatter.formatSource(source);
            }
            return 0;
        } catch (FormatterException e) {
            err.println("jfmt warmup failed: " + e.getMessage());
            return 1;
        }
    }

    private int formatStdin(JfmtFormatter formatter, InputStream in, PrintWriter out,
                            PrintWriter err) {
        try {
            String input = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String output = formatter.formatSource(input);
            out.print(output);
            out.flush();
            return 0;
        } catch (FormatterException | IOException e) {
            err.println("<stdin>: " + e.getMessage());
            return 1;
        }
    }
}
