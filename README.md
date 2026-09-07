# jfmt

[![Maven Central](https://img.shields.io/maven-central/v/com.netflix/com.netflix.tools.jfmt)](https://central.sonatype.com/artifact/com.netflix/com.netflix.tools.jfmt)
![JDK 25+](https://img.shields.io/badge/JDK-25%2B-blue)

`jfmt` formats modern Java source consistently using the [Sun Code Conventions for the Java Programming Language](https://www.oracle.com/technetwork/java/codeconventions-150003.pdf) as its baseline. Where the language and common practice have evolved, it follows the conventions used in current OpenJDK source.

Rather than imposing a fixed line width, `jfmt` combines Java structure with the visual distance between related peers. Horizontal distance is a continuous layout cost, not a maximum: identifier and literal length do not by themselves cause a line break, and indivisible code may remain long. Lists, expressions, and method chains become vertical when the reduction in scanning effort outweighs the fragmentation caused by additional lines.

- 4-space block indentation and 8-space continuation indentation
- Breaks before operators
- File, standard input, and module source path support
- Import ordering, wildcard expansion, qualified type simplification, and unused import removal
- Javadoc formatting following the OpenJDK 80-column convention

The formatter is built on [google-java-format](https://github.com/google/google-java-format).

## Installation

> [!IMPORTANT]
> This tool is currently in preview. We are collecting all preview feedback in the [`ja` repository](https://github.com/Netflix/ja): use [Issues](https://github.com/Netflix/ja/issues) to report problems and [Discussions](https://github.com/Netflix/ja/discussions) for feedback, questions, and suggestions.

Follow the `ja` [Installation Guide](https://github.com/Netflix/ja#installation) to create a `ja`-enabled development JDK. It includes the `jfmt` command and uses it for `ja fmt`.

For standalone use, `jar` and `jmod` artifacts for the tool are available on Maven Central.

## Format source

Format the source modules selected by the current directory:

```sh
ja fmt
```

Use the standalone command to format files in place:

```sh
jfmt File.java AnotherFile.java
```

`jfmt` also orders imports and removes unused imports.

### Check formatting

Check formatting without changing files:

```sh
jfmt --check File.java AnotherFile.java
```

The command exits non-zero and reports each file that needs formatting.

### Format standard input

Read UTF-8 source from standard input and write the formatted source to standard output:

```sh
jfmt -
```

### Format modules

Format every Java source file beneath a module source path:

```sh
jfmt --module-source-path src
jfmt --module-source-path 'src/*/main/java'
jfmt --module-source-path com.example.application=src/application
```

`--module-source-path` accepts the same forms as `javac`: a directory containing module directories, a module pattern, or a `module=path` mapping. It can be repeated and combined with explicit files or `--check`.

Select one or more root modules:

```sh
jfmt --module-source-path src -m com.example.application
jfmt --module-source-path src --module com.example.application,com.example.library
```

`-m` and `--module` format only the named modules. Repeating the option replaces the previous selection.

### Semantic import normalization

For files with satisfiable module requirements, `jfmt` enters source declarations into javac's symbol table to expand wildcard imports and replace unambiguous qualified type references with explicit imports. Source files passed in one invocation are entered together, so their declarations are visible to one another. This does not attribute method bodies or reject unrelated compilation errors.

If a named module has requirements that cannot be satisfied by the supplied compile-time module path, `jfmt` performs syntax-only formatting for that module and leaves wildcard and qualified references intact. Other satisfiable modules still receive semantic import normalization.

The standalone command accepts javac context options including `--class-path`, `--module-path`, `--upgrade-module-path`, `--source-path`, `--module-source-path`, `--system`, `--add-modules`, `--limit-modules`, `--add-exports`, `--add-reads`, `--patch-module`, `--release`, `--source`, and `--enable-preview`, along with their standard short aliases. Standard-input formatting is syntax-only because it has no source-path identity.

### Run the standalone JAR

The JAR contains its runtime dependencies:

```sh
java -jar jfmt.jar File.java
```

## Style

`jfmt` applies a fixed style without project-specific configuration. Formatting decisions use Java structure and relative visual cost rather than a fixed number of characters on a line.

| Setting | Value |
|---------|-------|
| Block indent | 4 spaces |
| Continuation indent | 8 spaces |
| Code line limit | No hard limit; horizontal distance contributes to layout |
| Javadoc width | 80 columns |
| Import order | `import module`, `java.*`, `javax.*`, third-party, then `static` |
| Operators | Break before the operator |
| Braces | Required around non-empty control statement bodies; same line as the declaration |

Comments separating import groups are retained with their groups. These groups keep their authored order so comments are not detached from the imports they describe.

### Structural line breaks

Identifier and literal length do not by themselves cause a line break:

```java
return configurationResolver.resolveInheritedApplicationConfiguration(applicationEnvironment);
```

Calls, expressions, and chains break as their combined Java structure accumulates. Structure is carried across nested expressions, not considered independently at each pair of parentheses:

```java
downstream.push(SharedSecrets.getJavaUtilCollectionAccess()
        .listFromTrustedArrayNullsAllowed(window));
```

Lists have flat, wrapped, and broken candidate layouts. Structural analysis establishes the baseline layout, while relative visual cost can promote it or relocate its single continuation break. Wrapped lists take one higher-level separator break:

```java
invoke(one, two, three, four, five, six,
        seven);
```

When otherwise single-line elements fill two structural rows, the call uses a broken list instead of packing multiple continuation rows. Array initializers follow the same rule, and their element structure contributes to enclosing expressions even while the initializer remains flat. Child layouts contribute to their enclosing lists: broken children propagate, while wrapped children consume a structural row so the higher-level expression gets the first opportunity to break.

Lists compare flat, single-continuation, and broken candidate layouts. Keeping siblings on the same row incurs a continuous scanning cost based on their distance and immediate structure; taking or relocating a break incurs a fragmentation cost. There is no column cliff, and the length of a final or solitary element adds no scanning cost because breaking cannot shorten it.

The formatter chooses the minimum decomposition that exposes the top-level structure. An enclosing break owns that decomposition, so optional child wrapping is removed when the parent break already separates distant peers. It does not recursively expand every nested call to compensate for code that may be clearer after refactoring.

```java
int result = new CommandRunner(ModuleLayer.boot(), tools, new ToolCatalog(List.of()), () -> null)
        .run(commandLine, InputStream.nullInputStream(), new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));
```

```java
assertEquals(
        List.of("--module-path", "/path with spaces", "--add-modules", "a,b", "#value"),
        ArgumentFiles.parse(argumentFile));
```

```java
ToolDefinition formatter = new ToolDefinition(
        "alternative-formatter",
        "fmt",
        Optional.empty(),
        Optional.of("com.example.formatter"),
        "alternative-formatter",
        Optional.of("1"),
        Set.of("module-source-path"),
        List.of());
```

Parameter declarations are weighted more heavily than arguments. They retain the conventional two-row wrapped form and switch to a broken parameter list rather than wrapping onto a third row.

Continuation lines align with the first item when that keeps related code together:

```java
static void someMethod(int anArgument, Object anotherArgument, String yetAnotherArgument,
                       Object andStillAnotherArgument) {}
```

When alignment would move the continuation too far to the right, it falls back to the 8-space continuation indent:

```java
static <A, B> G<A, B> extraordinarilyLongMethodName(F<A, B> first,
        H<A, I<B>> second) {}
```

The same layout model applies to declarations, calls, expressions, method chains, lambdas, and modern Java constructs.

### Control statement braces

Non-empty control statement bodies use braces, including `return`, `throw`, `break`, `continue`, loop, expression, and `if`/`else` bodies. This follows the Sun convention even when a body contains only one statement.

### Comments

Javadoc prose follows the OpenJDK 80-column convention. Existing wrapping is retained when it remains readable, and words and inline tags such as `{@link}` stay intact.

Line comments and block comments retain their authored line breaks rather than being reflowed to a particular width.

## Build and test

Building `jfmt` requires a `ja`-enabled JDK 25 or later. Run the test suite from the repository root:

```sh
ja test
```

`ja` compiles the source modules as needed.

The production module is in `src/com.netflix.tools.jfmt/classes`. Its JMOD legal content is in `src/com.netflix.tools.jfmt/legal`.

## Publish

Export a local Maven repository containing the modular JAR and platform JMODs:

```sh
ja maven publish --jmod --module-version 1.2.3 repository
```

Release automation validates and uploads the exported repository.

## Origin and license

`jfmt` incorporates and adapts [google-java-format](https://github.com/google/google-java-format), Copyright 2015 Google Inc. Integrated dependencies are relocated beneath `com.netflix.tools.jfmt.internal` to avoid conflicts with application modules. [`INTERNAL.md`](src/com.netflix.tools.jfmt/INTERNAL.md) records their versions and provenance.

`jfmt` and its modifications are licensed under the [Apache License 2.0](LICENSE). Third-party components retain their own licenses, as recorded in [NOTICE](NOTICE).
