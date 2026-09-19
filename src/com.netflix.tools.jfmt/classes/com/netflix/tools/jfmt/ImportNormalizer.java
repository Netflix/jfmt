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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.IntersectionTypeTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTreePathScanner;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;

/** Normalizes imports using parsed source and javac's entered symbol table. */
final class ImportNormalizer {
    private ImportNormalizer() {}

    static Map<Path, String> normalize(Map<Path, String> sources, List<String> compilerOptions) throws AttributionException {
        if (sources.isEmpty()) {
            return sources;
        }

        JavaCompiler compiler = ServiceLoader.load(JavaCompiler.class)
                .findFirst()
                .orElseThrow(() -> new AttributionException("Java compiler is not available"));

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
            JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(null, fileManager, diagnostics, options, null, files);
            List<CompilationUnitTree> units = new ArrayList<>();
            task.parse().forEach(units::add);
            task.enter();
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
            JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(null, fileManager, diagnostics, options, null,
                    fileManager.getJavaFileObjectsFromPaths(List.of(descriptor)));
            task.parse();
            task.enter();
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
        if (unit.getModule() != null) {
            return source;
        }
        SourcePositions positions = trees.getSourcePositions();
        var resolver = new TypeResolver(unit, task.getElements());
        List<WildcardImport> wildcards = wildcardImports(unit, resolver, positions);
        var scanner = new UsageScanner(unit, trees, resolver, wildcards);
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
            if (wildcard.retain()) {
                continue;
            }
            for (String name : wildcard.usedNames()) {
                imports.add(wildcard.importName(name));
            }
            edits.add(new Edit(wildcard.start(), wildcard.end(), ""));
        }
        for (QualifiedType candidate : scanner.qualifiedTypes) {
            if (conflictingCandidateNames.contains(candidate.simpleName())
                    || candidate.scopeConflict()
                    || scanner.hasConflictingType(candidate.simpleName(), candidate.qualifiedName())) {
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

    private static List<WildcardImport> wildcardImports(CompilationUnitTree unit, TypeResolver resolver,
            SourcePositions positions) {
        List<WildcardImport> result = new ArrayList<>();
        for (ImportTree importTree : unit.getImports()) {
            Tree qualified = importTree.getQualifiedIdentifier();
            if (!(qualified instanceof MemberSelectTree select) || !select.getIdentifier().contentEquals("*")) {
                continue;
            }
            String ownerName = select.getExpression().toString();
            Element owner = importTree.isStatic()
                    ? resolver.type(ownerName)
                    : resolver.elements.getPackageElement(ownerName);
            if (importTree.isStatic() && !(owner instanceof TypeElement)
                    || !importTree.isStatic() && !(owner instanceof PackageElement)) {
                continue;
            }
            long start = positions.getStartPosition(unit, importTree);
            long end = positions.getEndPosition(unit, importTree);
            if (start >= 0 && end >= start) {
                result.add(new WildcardImport(importTree.isStatic(), owner, resolver.elements, start, end));
            }
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

    private static final class TypeResolver {
        private final CompilationUnitTree unit;
        private final Elements elements;
        private final String packageName;
        private final Map<String, Set<String>> explicitImports = new HashMap<>();
        private final List<String> wildcardPackages = new ArrayList<>();
        private final Map<String, TypeElement> resolvedNames = new HashMap<>();

        TypeResolver(CompilationUnitTree unit, Elements elements) {
            this.unit = unit;
            this.elements = elements;
            this.packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            for (ImportTree importTree : unit.getImports()) {
                if (importTree.isStatic()) {
                    continue;
                }
                String name = importTree.getQualifiedIdentifier().toString();
                if (name.endsWith(".*")) {
                    wildcardPackages.add(name.substring(0, name.length() - 2));
                    continue;
                }
                int separator = name.lastIndexOf('.');
                String simpleName = separator < 0 ? name : name.substring(separator + 1);
                explicitImports.computeIfAbsent(simpleName, ignored -> new LinkedHashSet<>()).add(name);
            }
        }

        TypeElement type(String name) {
            if (resolvedNames.containsKey(name)) {
                return resolvedNames.get(name);
            }
            Set<TypeElement> matches = new LinkedHashSet<>();
            addType(matches, name);
            if (!packageName.isEmpty()) {
                addType(matches, packageName + "." + name);
            }
            for (Tree declaration : unit.getTypeDecls()) {
                if (declaration instanceof ClassTree type && !type.getSimpleName().isEmpty()) {
                    String owner = packageName.isEmpty()
                            ? type.getSimpleName().toString()
                            : packageName + "." + type.getSimpleName();
                    addType(matches, owner + "." + name);
                }
            }
            addType(matches, "java.lang." + name);

            int separator = name.indexOf('.');
            String first = separator < 0 ? name : name.substring(0, separator);
            String suffix = separator < 0 ? "" : name.substring(separator);
            for (String imported : explicitImports.getOrDefault(first, Set.of())) {
                addType(matches, imported + suffix);
            }
            for (String wildcardPackage : wildcardPackages) {
                addType(matches, wildcardPackage + "." + name);
            }
            TypeElement result = matches.size() == 1 ? matches.iterator().next() : null;
            resolvedNames.put(name, result);
            return result;
        }

        private void addType(Set<TypeElement> matches, String name) {
            TypeElement type = elements.getTypeElement(name);
            if (type != null) {
                matches.add(type);
            }
        }

        ResolvedType longestType(Tree tree) {
            return longestType(tree, true);
        }

        ResolvedType longestType(Tree tree, boolean typeContext) {
            List<String> names = new ArrayList<>();
            List<Tree> prefixes = new ArrayList<>();
            if (!qualifiedName(tree, names, prefixes)) {
                return null;
            }
            for (int count = names.size(); count > 0; count--) {
                String simpleName = names.get(count - 1);
                if (!typeContext && !Character.isUpperCase(simpleName.codePointAt(0))) {
                    continue;
                }
                TypeElement type = type(String.join(".", names.subList(0, count)));
                if (type != null) {
                    return new ResolvedType(type, prefixes.get(count - 1), count, names.size());
                }
            }
            return null;
        }

        private static boolean qualifiedName(Tree tree, List<String> names, List<Tree> prefixes) {
            if (tree instanceof IdentifierTree identifier) {
                names.add(identifier.getName().toString());
                prefixes.add(tree);
                return true;
            }
            if (!(tree instanceof MemberSelectTree select)
                    || select.getIdentifier().contentEquals("*")
                    || !qualifiedName(select.getExpression(), names, prefixes)) {
                return false;
            }
            names.add(select.getIdentifier().toString());
            prefixes.add(tree);
            return true;
        }
    }

    private static final class UsageScanner extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final Trees trees;
        private final TypeResolver resolver;
        private final List<WildcardImport> wildcards;
        private final Map<String, Set<String>> typeBindings = new HashMap<>();
        private final Set<String> valueBindings = new HashSet<>();
        private final Set<QualifiedType> qualifiedTypes = new LinkedHashSet<>();
        private boolean inImport;

        UsageScanner(CompilationUnitTree unit, Trees trees, TypeResolver resolver,
                List<WildcardImport> wildcards) {
            this.unit = unit;
            this.trees = trees;
            this.resolver = resolver;
            this.wildcards = wildcards;
            new DeclarationScanner(resolver, typeBindings, valueBindings).scan(unit, null);
            PackageElement sourcePackage = resolver.elements.getPackageElement(resolver.packageName);
            if (sourcePackage != null) {
                sourcePackage.getEnclosedElements().stream()
                        .filter(element -> element instanceof TypeElement)
                        .map(TypeElement.class::cast)
                        .forEach(this::recordTypeBinding);
            }
            for (ImportTree importTree : unit.getImports()) {
                Tree imported = importTree.getQualifiedIdentifier();
                if (imported instanceof MemberSelectTree select && select.getIdentifier().contentEquals("*")) {
                    continue;
                }
                ResolvedType resolved = resolver.longestType(imported);
                if (resolved != null && resolved.componentCount() == resolved.totalComponents()) {
                    recordTypeBinding(resolved.type());
                }
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
        public Void visitIdentifier(IdentifierTree tree, Void unused) {
            if (!inImport) {
                String name = tree.getName().toString();
                if (isPotentialTypeReference(getCurrentPath())) {
                    recordWildcardUse(name, isExpressionQualifier(getCurrentPath()), false);
                } else {
                    recordWildcardUse(name, false, true);
                }
            }
            return super.visitIdentifier(tree, unused);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
            TreePath parent = getCurrentPath().getParentPath();
            if (!inImport && (parent == null || !(parent.getLeaf() instanceof MemberSelectTree))) {
                ResolvedType resolved = resolver.longestType(tree, isPotentialTypeReference(getCurrentPath()));
                if (resolved != null && resolved.componentCount() > 1 && canImport(resolved.type())) {
                    Tree selection = resolved.selection();
                    long start = trees.getSourcePositions().getStartPosition(unit, selection);
                    long end = trees.getSourcePositions().getEndPosition(unit, selection);
                    if (start >= 0 && end >= start) {
                        TypeElement type = resolved.type();
                        String simpleName = type.getSimpleName().toString();
                        boolean expressionQualifier = resolved.componentCount() < resolved.totalComponents();
                        qualifiedTypes.add(new QualifiedType(simpleName, type.getQualifiedName().toString(), start,
                                end, needsImport(type), expressionQualifier && valueBindings.contains(simpleName)));
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
                            recordDocReference(reference.getSignature());
                            return super.visitReference(reference, unused);
                        }
                    }.scan(new DocTreePath(path, comment), null);
                }
            }.scan(unit, null);
        }

        private void recordDocReference(String signature) {
            int end = 0;
            while (end < signature.length() && Character.isJavaIdentifierPart(signature.charAt(end))) {
                end++;
            }
            if (end > 0) {
                recordWildcardUse(signature.substring(0, end), false, false);
            }
        }

        private void recordWildcardUse(String name, boolean expressionQualifier, boolean staticOnly) {
            List<WildcardImport> matches = wildcards.stream()
                    .filter(wildcard -> !staticOnly || wildcard.isStatic())
                    .filter(wildcard -> wildcard.matches(name))
                    .toList();
            if (matches.size() > 1 || expressionQualifier && valueBindings.contains(name)) {
                matches.forEach(WildcardImport::markRetained);
                return;
            }
            for (WildcardImport wildcard : matches) {
                if (wildcard.isStatic() && valueBindings.contains(name)) {
                    wildcard.markRetained();
                    continue;
                }
                wildcard.usedNames().add(name);
                TypeElement type = wildcard.type(name);
                if (type != null) {
                    recordTypeBinding(type);
                }
            }
        }

        private static boolean isExpressionQualifier(TreePath path) {
            TreePath parent = path.getParentPath();
            return parent != null
                    && (parent.getLeaf() instanceof MemberSelectTree select && select.getExpression() == path.getLeaf()
                    || parent.getLeaf() instanceof MemberReferenceTree reference && reference.getQualifierExpression() == path.getLeaf());
        }

        private static boolean isPotentialTypeReference(TreePath path) {
            Tree child = path.getLeaf();
            TreePath parentPath = path.getParentPath();
            while (parentPath != null) {
                Tree parent = parentPath.getLeaf();
                if (parent instanceof AnnotatedTypeTree annotated && annotated.getUnderlyingType() == child
                        || parent instanceof ArrayTypeTree array && array.getType() == child
                        || parent instanceof ParameterizedTypeTree parameterized
                                && (parameterized.getType() == child || parameterized.getTypeArguments().contains(child))) {
                    child = parent;
                    parentPath = parentPath.getParentPath();
                    continue;
                }
                return parent instanceof VariableTree variable && variable.getType() == child
                        || parent instanceof MethodTree method
                                && (method.getReturnType() == child || method.getThrows().contains(child))
                        || parent instanceof ClassTree declaration
                                && (declaration.getExtendsClause() == child
                                        || declaration.getImplementsClause().contains(child)
                                        || declaration.getPermitsClause().contains(child))
                        || parent instanceof TypeParameterTree parameter && parameter.getBounds().contains(child)
                        || parent instanceof TypeCastTree cast && cast.getType() == child
                        || parent instanceof InstanceOfTree instanceOf && instanceOf.getType() == child
                        || parent instanceof DeconstructionPatternTree pattern && pattern.getDeconstructor() == child
                        || parent instanceof NewClassTree creation && creation.getIdentifier() == child
                        || parent instanceof NewArrayTree creation && creation.getType() == child
                        || parent instanceof AnnotationTree annotation && annotation.getAnnotationType() == child
                        || parent instanceof WildcardTree wildcard && wildcard.getBound() == child
                        || parent instanceof UnionTypeTree union && union.getTypeAlternatives().contains(child)
                        || parent instanceof IntersectionTypeTree intersection && intersection.getBounds().contains(child)
                        || parent instanceof MethodInvocationTree invocation && invocation.getTypeArguments().contains(child)
                        || parent instanceof MemberReferenceTree reference
                                && (reference.getQualifierExpression() == child || reference.getTypeArguments().contains(child))
                        || parent instanceof MemberSelectTree select && select.getExpression() == child;
            }
            return false;
        }

        private static boolean canImport(TypeElement type) {
            Element current = type;
            while (current.getEnclosingElement() != null
                    && !(current.getEnclosingElement() instanceof PackageElement)) {
                current = current.getEnclosingElement();
            }
            if (!(current.getEnclosingElement() instanceof PackageElement owner)) {
                return false;
            }
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

        private void recordTypeBinding(TypeElement type) {
            typeBindings.computeIfAbsent(type.getSimpleName().toString(), ignored -> new HashSet<>())
                    .add(type.getQualifiedName().toString());
        }
    }

    private static final class DeclarationScanner extends TreePathScanner<Void, Void> {
        private final TypeResolver resolver;
        private final Map<String, Set<String>> typeBindings;
        private final Set<String> valueBindings;
        private final Deque<String> enclosingTypes = new ArrayDeque<>();

        DeclarationScanner(TypeResolver resolver, Map<String, Set<String>> typeBindings,
                Set<String> valueBindings) {
            this.resolver = resolver;
            this.typeBindings = typeBindings;
            this.valueBindings = valueBindings;
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            String simpleName = tree.getSimpleName().toString();
            if (simpleName.isEmpty()) {
                return super.visitClass(tree, unused);
            }
            enclosingTypes.addLast(simpleName);
            String relativeName = String.join(".", enclosingTypes);
            String qualifiedName = resolver.packageName.isEmpty()
                    ? relativeName
                    : resolver.packageName + "." + relativeName;
            TypeElement type = resolver.elements.getTypeElement(qualifiedName);
            typeBindings.computeIfAbsent(simpleName, ignored -> new HashSet<>())
                    .add(type == null ? qualifiedName : type.getQualifiedName().toString());
            try {
                return super.visitClass(tree, unused);
            } finally {
                enclosingTypes.removeLast();
            }
        }

        @Override
        public Void visitTypeParameter(TypeParameterTree tree, Void unused) {
            typeBindings.computeIfAbsent(tree.getName().toString(), ignored -> new HashSet<>())
                    .add("<type parameter>");
            return super.visitTypeParameter(tree, unused);
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            valueBindings.add(tree.getName().toString());
            return super.visitVariable(tree, unused);
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            valueBindings.add(tree.getName().toString());
            return super.visitMethod(tree, unused);
        }
    }

    private static final class WildcardImport {
        private final boolean isStatic;
        private final Element owner;
        private final Elements elements;
        private final long start;
        private final long end;
        private final Set<String> staticMembers = new HashSet<>();
        private final Map<String, TypeElement> types = new HashMap<>();
        private final Set<String> usedNames = new LinkedHashSet<>();
        private boolean retain;

        WildcardImport(boolean isStatic, Element owner, Elements elements, long start, long end) {
            this.isStatic = isStatic;
            this.owner = owner;
            this.elements = elements;
            this.start = start;
            this.end = end;
            if (isStatic) {
                for (Element member : elements.getAllMembers((TypeElement) owner)) {
                    if (!member.getModifiers().contains(Modifier.STATIC)) {
                        continue;
                    }
                    String name = member.getSimpleName().toString();
                    staticMembers.add(name);
                    if (member instanceof TypeElement type) {
                        types.putIfAbsent(name, type);
                    }
                }
            }
        }

        boolean matches(String name) {
            return isStatic ? staticMembers.contains(name) : type(name) != null;
        }

        TypeElement type(String name) {
            if (types.containsKey(name)) {
                return types.get(name);
            }
            TypeElement type = isStatic ? null : elements.getTypeElement(owner + "." + name);
            types.put(name, type);
            return type;
        }

        void markRetained() {
            retain = true;
        }

        boolean retain() {
            return retain;
        }

        boolean isStatic() {
            return isStatic;
        }

        long start() {
            return start;
        }

        long end() {
            return end;
        }

        Set<String> usedNames() {
            return usedNames;
        }

        String importName(String name) {
            String prefix = isStatic ? "import static " : "import ";
            return prefix + owner + "." + name + ";\n";
        }
    }

    private record ResolvedType(TypeElement type, Tree selection, int componentCount,
            int totalComponents) {}

    private record QualifiedType(String simpleName, String qualifiedName, long start,
            long end, boolean needsImport, boolean scopeConflict) {}

    private record Edit(long start, long end, String replacement) {}

    static final class AttributionException extends Exception {
        AttributionException(String message) {
            super(message);
        }
    }
}
