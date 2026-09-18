# jfmt

[![Maven Central](https://img.shields.io/maven-central/v/com.netflix/com.netflix.tools.jfmt)](https://central.sonatype.com/artifact/com.netflix/com.netflix.tools.jfmt)
![JDK 25+](https://img.shields.io/badge/JDK-25%2B-blue)

`jfmt` formats modern Java source according to the [Code Conventions for the Java Programming Language](https://www.oracle.com/java/technologies/javase/codeconventions-introduction.html), with updates for current Java syntax and OpenJDK practice.

It uses 4 spaces for block indentation, 8 spaces for continuation indentation, and places operators at the beginning of continuation lines. It does not impose a maximum length on code lines. Instead, it breaks lists, expressions, and method chains at syntactic boundaries. Long identifiers and literals do not cause a line break by themselves.

Import processing orders imports, removes unused imports, expands wildcards when the required types are available, and shortens unambiguous qualified type names by adding imports. Javadoc prose is formatted to 80 columns. Sources can be supplied as individual files, through standard input, or by module source path.

The formatter is built on [google-java-format](https://github.com/google/google-java-format).

> [!IMPORTANT]
> This tool is currently in preview. We are collecting feedback for all of the tools together in [Discussions](https://github.com/Netflix/ja/discussions).

## Installation

> [!NOTE]
> Netflix engineers should use the internally bundled toolchain rather than installing `jfmt` separately.

### With `ja`

Follow the `ja` [Installation Guide](https://github.com/Netflix/ja#installation) to create a `ja`-enabled development JDK. The JDK includes the `jfmt` command, and `ja fmt` uses it when formatting source modules.

### Standalone

Standalone use requires JDK 25 or later. Download the executable JAR from [Maven Central](https://central.sonatype.com/artifact/com.netflix/com.netflix.tools.jfmt). The JAR contains its runtime dependencies:

```sh
java -jar com.netflix.tools.jfmt-VERSION.jar File.java
```

JMOD artifacts are also published for building custom runtime images.

## Format source

From a `ja` project, format the source modules selected by the current directory:

```sh
ja fmt
```

To format specific files, pass them to `jfmt`:

```sh
jfmt File.java AnotherFile.java
```

The command rewrites the files in place. It also orders imports and removes unused imports.

> [!TIP]
> The examples below use the installed `jfmt` command. When using the standalone JAR, replace `jfmt` with `java -jar com.netflix.tools.jfmt-VERSION.jar`.

### Check formatting

Use `--check` in CI or before committing to find files that would be changed without rewriting them:

```sh
jfmt --check File.java AnotherFile.java
```

The command reports each file that needs formatting and exits with a non-zero status if it finds any.

### Format standard input

Use `-` to read UTF-8 source from standard input and write the formatted source to standard output:

```sh
jfmt -
```

This form is useful for editor integration and shell pipelines. It does not perform type-aware import normalization because standard input has no source-path identity.

### Format modules

Use `--module-source-path` to discover and format Java files by module instead of listing each file:

```sh
jfmt --module-source-path src
jfmt --module-source-path 'src/*/main/java'
jfmt --module-source-path com.example.application=src/application
```

The option accepts the same path forms as `javac`:

- `src` finds modules in immediate subdirectories of `src`.
- `'src/*/main/java'` substitutes each module name for `*`. Quote the pattern to prevent the shell from expanding it.
- `com.example.application=src/application` assigns a source directory to a specific module.

Without `--module`, every module found on the module source path is formatted. Use `-m` or `--module` to select particular modules:

```sh
jfmt --module-source-path src -m com.example.application
jfmt --module-source-path src --module com.example.application,com.example.library
```

`--module-source-path` may be repeated and combined with explicit files or `--check`. A later `-m` or `--module` option replaces an earlier module selection.

### Normalize imports

Import processing can also:

- replace a wildcard import with imports for the types the source actually uses;
- shorten a fully qualified type name by adding an import, provided the shorter name is unambiguous.

For example:

```java
import java.util.*;

class Example {
    java.time.Duration timeout;
    List<String> names;
}
```

becomes:

```java
import java.time.Duration;
import java.util.List;

class Example {
    Duration timeout;
    List<String> names;
}
```

Type names are resolved with `javac` before making these changes. This prevents an import from being added when it conflicts with another declaration or import. Pass related source files in the same invocation so their declarations are available to one another:

```sh
jfmt dependency/Widget.java application/Example.java
```

Use the same class path or module path as the build so `jfmt` can resolve referenced types:

```sh
jfmt --class-path build/classes application/Example.java
jfmt --module-path modules --module-source-path src
```

Other `javac` options that affect type resolution are also supported. Run `jfmt --help` for the complete list.

Method bodies are not compiled as part of type resolution. A missing type needed to resolve a declaration causes the command to fail before changing any files, but unrelated errors inside method bodies do not prevent formatting.

If the requirements of a named module are unavailable on the supplied module path, the module is still formatted, but its wildcard imports and qualified type names are left unchanged. Other modules whose requirements are available still receive type-aware import normalization.

## Style

The formatter has one built-in style and does not read project-specific formatting configuration.

| Setting | Behavior |
|---------|----------|
| Block indentation | 4 spaces |
| Continuation indentation | 8 spaces |
| Code lines | No fixed maximum length; breaks are based on Java syntax |
| Javadoc | Wrapped to 80 columns |
| Imports | `import module`, `java.*` and `javax.*`, third-party, then `static` |
| Operators | Placed at the beginning of a continuation line |
| Control statement braces | Required around non-empty bodies |
| Opening braces | Kept on the same line as the declaration or control statement |

Comments that divide imports into groups remain attached to their groups. When such comments are present, `jfmt` preserves the authored order of the groups rather than moving imports away from the comments that describe them.

### Structural line breaks

A fixed line-length limit cannot distinguish a long name from an expression containing many separate parts. Line breaks are instead inserted at boundaries in Java syntax. This keeps the parts of calls, expressions, and method chains from becoming widely separated without breaking merely because an identifier or literal is long.

A long identifier can therefore remain on one line:

```java
return configurationResolver.resolveInheritedApplicationConfiguration(applicationEnvironment);
```

As an expression gains nested calls, operators, or chain elements, continuation lines expose those boundaries:

```java
downstream.push(SharedSecrets.getJavaUtilCollectionAccess()
        .listFromTrustedArrayNullsAllowed(window));
```

A list may remain on one line, continue onto a second line, or place each element on its own line. A single continuation is used when one break is enough:

```java
invoke(one, two, three, four, five, six,
        seven);
```

Longer or more structured lists may use one element per line:

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

For nested calls, the outer arguments are separated first. Nested lists do not become vertical merely because the enclosing call breaks:

```java
assertEquals(
        List.of("--module-path", "/path with spaces", "--add-modules", "a,b", "#value"),
        ArgumentFiles.parse(argumentFile));
```

Continuation lines in declarations align with the first parameter when that alignment remains practical:

```java
static void someMethod(int anArgument, Object anotherArgument, String yetAnotherArgument,
                       Object andStillAnotherArgument) {}
```

When the first parameter is too far to the right, continuation lines use the standard 8-space continuation indentation:

```java
static <A, B> G<A, B> extraordinarilyLongMethodName(F<A, B> first,
        H<A, I<B>> second) {}
```

### Control statement braces

Braces are added around non-empty `if`, `else`, `for`, `while`, and `do` bodies, even when the body contains only one statement:

```java
if (ready) {
    start();
}
```

This follows the Java code conventions, makes the extent of the controlled body explicit, and prevents a newly added statement from accidentally falling outside it. Empty statement bodies and `else if` chains are left in their conventional forms.

### Comments

Javadoc prose is wrapped to 80 columns. Existing wrapping that already fits is preserved, as are words, inline tags such as `{@link}`, and preformatted content. These elements may extend beyond column 80.

Line and block comments are not reflowed, preserving deliberately arranged lists, diagrams, or code.

## Origin and license

The formatter incorporates and adapts [google-java-format](https://github.com/google/google-java-format), Copyright 2015 Google Inc. Integrated dependencies are relocated beneath `com.netflix.tools.jfmt.internal` to prevent conflicts with application modules. [`INTERNAL.md`](src/com.netflix.tools.jfmt/INTERNAL.md) records their versions and provenance.

The project is licensed under the [Apache License 2.0](LICENSE). Third-party components retain the licenses recorded in [NOTICE](NOTICE).
