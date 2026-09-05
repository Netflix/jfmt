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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.Tree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTreePathScanner;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

/**
 * Normalizes imports only when javac can attribute the complete set of source
 * files.
 */
final class ImportNormalizer {
    private ImportNormalizer() {}

    static Map<Path, String> normalize(Map<Path, String> sources, List<String> compilerOptions) throws AttributionException {
        if (sources.isEmpty()) {
            return sources;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new AttributionException("Java compiler is not available");
        }

        List<String> options = attributionOptions(compilerOptions);
        List<Path> descriptors = sources.keySet().stream()
                .filter(path -> path.getFileName()
                                    .toString()
                                    .equals("module-info.java"))
                .toList();
        Map<Path, Boolean> satisfiableModules = new HashMap<>();
        for (Path descriptor : descriptors) {
            satisfiableModules.put(descriptor.getParent(), requirementsSatisfied(compiler, descriptor, options));
        }

        Map<Path, String> namedSources = new LinkedHashMap<>();
        Map<Path, String> unnamedSources = new LinkedHashMap<>();
        for (var source : sources.entrySet()) {
            Path module = enclosingModule(source.getKey(), descriptors);
            if (module == null) {
                unnamedSources.put(source.getKey(), source.getValue());
            } else if (satisfiableModules.get(module)) {
                namedSources.put(source.getKey(), source.getValue());
            }
        }

        Map<Path, String> replacements = new HashMap<>();
        replacements.putAll(attribute(compiler, namedSources, options));
        replacements.putAll(attribute(compiler, unnamedSources, options));
        Map<Path, String> result = new LinkedHashMap<>();
        for (var source : sources.entrySet()) {
            result.put(source.getKey(),
                    replacements.getOrDefault(source.getKey(), source.getValue()));
        }
        return result;
    }

    private static Path enclosingModule(Path source, List<Path> descriptors) {
        Path result = null;
        for (Path descriptor : descriptors) {
            Path root = descriptor.getParent();
            if (source.startsWith(root) && (result == null || root.getNameCount() > result.getNameCount())) {
                result = root;
            }
        }
        return result;
    }

    private static Map<Path, String> attribute(JavaCompiler compiler, Map<Path, String> sources, List<String> options) throws AttributionException {
        if (sources.isEmpty()) {
            return Map.of();
        }
        Path classOutput = null;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            classOutput = Files.createTempDirectory("jfmt-attribution-");
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutput));
            List<Path> paths = List.copyOf(sources.keySet());
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            Iterable<? extends JavaFileObject> files = fileManager.getJavaFileObjectsFromPaths(paths);
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, options, null, files);
            List<CompilationUnitTree> units = new ArrayList<>();
            task.parse().forEach(units::add);
            task.analyze();
            if (hasErrors(diagnostics)) {
                throw new AttributionException(diagnosticMessage(diagnostics));
            }

            Trees trees = Trees.instance(task);
            Map<URI, Path> pathsByUri = new HashMap<>();
            for (Path path : paths) {
                pathsByUri.put(path.toAbsolutePath()
                                   .normalize()
                                   .toUri()
                                   .normalize(),
                        path);
            }

            Map<Path, String> normalized = new LinkedHashMap<>();
            for (CompilationUnitTree unit : units) {
                Path path = pathsByUri.get(unit.getSourceFile()
                        .toUri()
                        .normalize());
                if (path == null) {
                    throw new AttributionException("compiler returned an unexpected source file");
                }
                normalized.put(path, normalizeUnit(sources.get(path), unit, trees, task));
            }
            if (normalized.size() != sources.size()) {
                throw new AttributionException("compiler did not return every source file");
            }
            return normalized;
        } catch (IOException | RuntimeException e) {
            throw attributionFailure(e);
        } finally {
            deleteTree(classOutput);
        }
    }

    private static List<String> attributionOptions(List<String> compilerOptions) {
        List<String> options = new ArrayList<>();
        for (int i = 0; i < compilerOptions.size(); i++) {
            String option = compilerOptions.get(i);
            if (option.equals("--module")) {
                i++;
                continue;
            }
            options.add(option);
        }
        options.add("-proc:none");
        options.add("-implicit:none");
        return options;
    }

    private static boolean requirementsSatisfied(JavaCompiler compiler, Path descriptor, List<String> options) throws AttributionException {
        Path classOutput = null;
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            classOutput = Files.createTempDirectory("jfmt-attribution-");
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classOutput));
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics, options, null,
                    fileManager.getJavaFileObjectsFromPaths(List.of(descriptor)));
            task.parse();
            task.analyze();
        } catch (IOException | RuntimeException e) {
            throw attributionFailure(e);
        } finally {
            deleteTree(classOutput);
        }
        return diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Kind.ERROR)
                .noneMatch(diagnostic -> diagnostic.getCode().contains("module.not.found"));
    }

    private static AttributionException attributionFailure(Exception exception) {
        String message = exception.getMessage();
        return new AttributionException(message == null || message.isBlank()
                ? exception.toString()
                : message);
    }

    private static boolean hasErrors(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.getKind() == Kind.ERROR);
    }

    private static String diagnosticMessage(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Kind.ERROR)
                .map(Diagnostic::toString)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("source attribution failed");
    }

    private static void deleteTree(Path root) {
        if (root == null) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Failure to remove an empty compiler output location must not prevent formatting.
        }
    }

    private static String normalizeUnit(String source, CompilationUnitTree unit, Trees trees,
            JavacTask task) {
        SourcePositions positions = trees.getSourcePositions();
        List<WildcardImport> wildcards = wildcardImports(unit, trees, task, positions);
        var scanner = new UsageScanner(unit, trees, wildcards);
        scanner.scan(unit, null);
        scanner.scanDocComments(DocTrees.instance(task));

        Set<String> conflictingCandidateNames = new HashSet<>();
        Map<String, String> candidateNames = new HashMap<>();
        for (QualifiedType candidate : scanner.qualifiedTypes) {
            String previous = candidateNames.putIfAbsent(candidate.simpleName(), candidate.qualifiedName());
            if (previous != null && !previous.equals(candidate.qualifiedName())) {
                conflictingCandidateNames.add(candidate.simpleName());
            }
        }

        Set<String> imports = new LinkedHashSet<>();
        List<Edit> edits = new ArrayList<>();
        for (WildcardImport wildcard : wildcards) {
            for (String name : wildcard.usedNames()) {
                imports.add(wildcard.importName(name));
            }
            edits.add(new Edit(wildcard.start(), wildcard.end(), ""));
        }
        for (QualifiedType candidate : scanner.qualifiedTypes) {
            if (conflictingCandidateNames.contains(candidate.simpleName()) || candidate.scopeConflict() || scanner.hasConflictingType(candidate.simpleName(), candidate.qualifiedName())) {
                continue;
            }
            edits.add(new Edit(candidate.start(), candidate.end(), candidate.simpleName()));
            if (candidate.needsImport()) {
                imports.add("import " + candidate.qualifiedName() + ";\n");
            }
        }

        if (edits.isEmpty() && imports.isEmpty()) {
            return source;
        }
        edits.sort((left, right) -> {
            int start = Long.compare(right.start(), left.start());
            return start != 0 ? start : Long.compare(right.end(), left.end());
        });
        StringBuilder result = new StringBuilder(source);
        for (Edit edit : edits) {
            result.replace(Math.toIntExact(edit.start()), Math.toIntExact(edit.end()), edit.replacement());
        }
        if (!imports.isEmpty()) {
            long insertionPoint = importInsertionPoint(unit, positions);
            result.insert(Math.toIntExact(insertionPoint), String.join("", imports));
        }
        return result.toString();
    }

    private static List<WildcardImport> wildcardImports(CompilationUnitTree unit, Trees trees, JavacTask task,
            SourcePositions positions) {
        List<WildcardImport> result = new ArrayList<>();
        for (ImportTree importTree : unit.getImports()) {
            Tree qualified = importTree.getQualifiedIdentifier();
            if (!(qualified instanceof MemberSelectTree select) || !select.getIdentifier().contentEquals("*")) {
                continue;
            }
            TreePath expressionPath = TreePath.getPath(unit, select.getExpression());
            Element owner = expressionPath == null ? null : trees.getElement(expressionPath);
            if (importTree.isStatic() && !(owner instanceof TypeElement)) {
                continue;
            }
            if (!importTree.isStatic() && !(owner instanceof PackageElement)) {
                continue;
            }
            long start = positions.getStartPosition(unit, importTree);
            long end = positions.getEndPosition(unit, importTree);
            if (start < 0 || end < start) {
                continue;
            }
            Set<Element> members = new HashSet<>();
            if (owner instanceof TypeElement type) {
                members.addAll(task.getElements()
                                   .getAllMembers(type));
            }
            result.add(new WildcardImport(importTree.isStatic(), owner, members, start, end,
                    new LinkedHashSet<>()));
        }
        return result;
    }

    private static long importInsertionPoint(CompilationUnitTree unit, SourcePositions positions) {
        if (!unit.getImports().isEmpty()) {
            return positions.getStartPosition(unit, unit.getImports()
                    .getFirst());
        }
        if (unit.getPackage() != null) {
            return positions.getEndPosition(unit, unit.getPackage());
        }
        if (!unit.getTypeDecls().isEmpty()) {
            return positions.getStartPosition(unit, unit.getTypeDecls()
                    .getFirst());
        }
        return 0;
    }

    private static final class UsageScanner extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final Trees trees;
        private final List<WildcardImport> wildcards;
        private final Map<String, Set<String>> typeBindings = new HashMap<>();
        private final List<QualifiedType> qualifiedTypes = new ArrayList<>();
        private boolean inImport;

        UsageScanner(CompilationUnitTree unit, Trees trees, List<WildcardImport> wildcards) {
            this.unit = unit;
            this.trees = trees;
            this.wildcards = wildcards;
            for (ImportTree importTree : unit.getImports()) {
                Tree imported = importTree.getQualifiedIdentifier();
                if (imported instanceof MemberSelectTree select && select.getIdentifier().contentEquals("*")) {
                    continue;
                }
                recordTypeBinding(trees.getElement(TreePath.getPath(unit, imported)));
            }
        }

        @Override
        public Void visitImport(ImportTree tree, Void unused) {
            boolean previous = inImport;
            inImport = true;
            super.visitImport(tree, unused);
            inImport = previous;
            return null;
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            recordTypeBinding(trees.getElement(getCurrentPath()));
            return super.visitClass(tree, unused);
        }

        @Override
        public Void visitIdentifier(IdentifierTree tree, Void unused) {
            if (!inImport) {
                Element element = trees.getElement(getCurrentPath());
                recordTypeBinding(element);
                for (WildcardImport wildcard : wildcards) {
                    if (wildcard.contains(element)) {
                        wildcard.usedNames().add(tree.getName()
                                .toString());
                    }
                }
            }
            return super.visitIdentifier(tree, unused);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
            if (!inImport) {
                Element element = trees.getElement(getCurrentPath());
                if (element instanceof TypeElement type && isOutermostTypeSelection()) {
                    String qualifiedName = type.getQualifiedName().toString();
                    long start = trees.getSourcePositions().getStartPosition(unit, tree);
                    long end = trees.getSourcePositions().getEndPosition(unit, tree);
                    if (!qualifiedName.isEmpty()
                            && canImport(type)
                            && start >= 0
                            && end >= start) {
                        String simpleName = type.getSimpleName().toString();
                        qualifiedTypes.add(new QualifiedType(simpleName, qualifiedName, start, end, needsImport(type),
                                isExpressionQualifier() && hasScopeConflict(simpleName, qualifiedName)));
                    }
                }
            }
            return super.visitMemberSelect(tree, unused);
        }

        boolean hasConflictingType(String simpleName, String qualifiedName) {
            return typeBindings.getOrDefault(simpleName, Set.of()).stream()
                    .anyMatch(binding -> !binding.equals(qualifiedName));
        }

        void scanDocComments(DocTrees docTrees) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void scan(Tree tree, Void unused) {
                    if (tree != null && getCurrentPath() != null) {
                        recordDocComment(new TreePath(getCurrentPath(), tree));
                    }
                    return super.scan(tree, unused);
                }

                @Override
                public Void scan(TreePath path, Void unused) {
                    recordDocComment(path);
                    return super.scan(path, unused);
                }

                private void recordDocComment(TreePath path) {
                    DocCommentTree comment = docTrees.getDocCommentTree(path);
                    if (comment == null) {
                        return;
                    }
                    new DocTreePathScanner<Void, Void>() {
                        @Override
                        public Void visitReference(ReferenceTree reference, Void unused) {
                            recordDocReference(docTrees.getElement(getCurrentPath()), reference.getSignature());
                            return super.visitReference(reference, unused);
                        }
                    }.scan(new DocTreePath(path, comment), null);
                }
            }.scan(unit, null);
        }

        private void recordDocReference(Element element, String signature) {
            for (WildcardImport wildcard : wildcards) {
                if (wildcard.isStatic()) {
                    if (wildcard.contains(element) && isSimpleDocReference(signature, element.getSimpleName()
                            .toString())) {
                        wildcard.usedNames().add(element.getSimpleName()
                                .toString());
                    }
                    continue;
                }
                TypeElement topLevelType = topLevelType(element);
                if (topLevelType != null && wildcard.contains(topLevelType) && isSimpleDocReference(signature, topLevelType.getSimpleName()
                        .toString())) {
                    wildcard.usedNames().add(topLevelType.getSimpleName()
                            .toString());
                }
            }
        }

        private static TypeElement topLevelType(Element element) {
            TypeElement result = null;
            for (Element current = element;
                 current != null && !(current instanceof PackageElement);
                 current = current.getEnclosingElement()) {
                if (current instanceof TypeElement type) {
                    result = type;
                }
            }
            return result;
        }

        private static boolean isSimpleDocReference(String signature, String name) {
            if (!signature.startsWith(name)) {
                return false;
            }
            if (signature.length() == name.length()) {
                return true;
            }
            return switch (signature.charAt(name.length())) {
                case '#', '.', '<', '[', '(' -> true;
                default -> false;
            };
        }

        private boolean isOutermostTypeSelection() {
            TreePath parent = getCurrentPath().getParentPath();
            return parent == null || !(parent.getLeaf() instanceof MemberSelectTree) || !(trees.getElement(parent) instanceof TypeElement);
        }

        private boolean isExpressionQualifier() {
            TreePath parent = getCurrentPath().getParentPath();
            return parent != null && parent.getLeaf() instanceof MemberSelectTree && !(trees.getElement(parent) instanceof TypeElement);
        }

        private boolean hasScopeConflict(String simpleName, String qualifiedName) {
            for (Scope scope = trees.getScope(getCurrentPath());
                 scope != null;
                 scope = scope.getEnclosingScope()) {
                for (Element element : scope.getLocalElements()) {
                    if (!element.getSimpleName().contentEquals(simpleName)) {
                        continue;
                    }
                    if (element instanceof TypeElement type && type.getQualifiedName().contentEquals(qualifiedName)) {
                        continue;
                    }
                    if (element.getKind() != ElementKind.PACKAGE && element.getKind() != ElementKind.MODULE) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean canImport(TypeElement type) {
            Element current = type;
            while (!(current.getEnclosingElement() instanceof PackageElement)) {
                current = current.getEnclosingElement();
            }
            PackageElement owner = (PackageElement) current.getEnclosingElement();
            return current == type || !owner.isUnnamed();
        }

        private boolean needsImport(TypeElement type) {
            Element enclosing = type.getEnclosingElement();
            if (!(enclosing instanceof PackageElement owner)) {
                return true;
            }
            String ownerName = owner.getQualifiedName().toString();
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            return !ownerName.equals(packageName) && !ownerName.equals("java.lang");
        }

        private void recordTypeBinding(Element element) {
            if (element instanceof TypeElement type) {
                typeBindings.computeIfAbsent(type.getSimpleName().toString(),
                        ignored -> new HashSet<>())
                            .add(type.getQualifiedName()
                                     .toString());
            } else if (element != null && element.getKind() == ElementKind.TYPE_PARAMETER) {
                typeBindings.computeIfAbsent(element.getSimpleName().toString(),
                        ignored -> new HashSet<>())
                            .add("<type parameter>");
            }
        }
    }

    private record WildcardImport(boolean isStatic, Element owner, Set<Element> members,
            long start, long end, Set<String> usedNames) {
        boolean contains(Element element) {
            if (element == null) {
                return false;
            }
            if (isStatic) {
                return element.getModifiers().contains(Modifier.STATIC) && members.contains(element);
            }
            return element instanceof TypeElement && element.getEnclosingElement().equals(owner);
        }

        String importName(String name) {
            String prefix = isStatic ? "import static " : "import ";
            return prefix + owner + "." + name + ";\n";
        }
    }

    private record QualifiedType(String simpleName, String qualifiedName, long start,
            long end, boolean needsImport, boolean scopeConflict) {}

    private record Edit(long start, long end, String replacement) {}

    static final class AttributionException extends Exception {
        AttributionException(String message) {
            super(message);
        }
    }
}
