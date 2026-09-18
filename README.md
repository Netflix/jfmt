# jfmt

[![Maven Central](https://img.shields.io/maven-central/v/com.netflix/com.netflix.tools.jfmt)](https://central.sonatype.com/artifact/com.netflix/com.netflix.tools.jfmt)
![JDK 25+](https://img.shields.io/badge/JDK-25%2B-blue)

`jfmt` formats modern Java source according to the [Code Conventions for the Java Programming Language](https://www.oracle.com/java/technologies/javase/codeconventions-introduction.html), with updates for current Java syntax and OpenJDK practice.

Capabilities include:

- Use 4 spaces for block indentation and 8 spaces for continuation indentation
- Break lists, expressions, and method chains at syntactic boundaries without imposing a maximum code-line length
- Order imports, remove unused imports, expand wildcards, and shorten unambiguous qualified type names
- Format Javadoc prose to 80 columns
- Read individual files, standard input, or a module source path

The formatter is built on [google-java-format](https://github.com/google/google-java-format).

> [!IMPORTANT]
> This tool is currently in preview. Please share feedback for any of the tools in [Discussions](https://github.com/Netflix/ja/discussions).

## Installation

> [!NOTE]
> Netflix engineers should use the internally bundled toolchain rather than installing this tool separately.

Follow the `ja` [Installation Guide](https://github.com/Netflix/ja#installation) to create a `ja`-enabled development JDK. The JDK includes the `jfmt` command, and `ja fmt` uses it when formatting source modules.

For standalone use, download the executable JAR from [Maven Central](https://central.sonatype.com/artifact/com.netflix/com.netflix.tools.jfmt). JDK 25 or later is required. The JAR contains its runtime dependencies:

```sh
java -jar com.netflix.tools.jfmt-VERSION.jar File.java
```

JMOD artifacts are also published for building custom runtime images.

## Quick start

Format the source modules selected by the current directory in a `ja` project:

```sh
ja fmt
```

Format specific files in place:

```sh
jfmt File.java AnotherFile.java
```

Check for files that would change without rewriting them:

```sh
jfmt --check File.java AnotherFile.java
```

Format UTF-8 source from standard input:

```sh
jfmt -
```

## Documentation

The [wiki](https://github.com/Netflix/jfmt/wiki) covers:

- [Formatting source](https://github.com/Netflix/jfmt/wiki/Formatting-Source)
- [Normalizing imports](https://github.com/Netflix/jfmt/wiki/Normalizing-Imports)
- [Style](https://github.com/Netflix/jfmt/wiki/Style)

## Origin and license

The formatter incorporates and adapts [google-java-format](https://github.com/google/google-java-format), Copyright 2015 Google Inc. Integrated dependencies are relocated beneath `com.netflix.tools.jfmt.internal` to prevent conflicts with application modules. [`INTERNAL.md`](src/com.netflix.tools.jfmt/INTERNAL.md) records their versions and provenance.

The project is licensed under the [Apache License 2.0](LICENSE). Third-party components retain the licenses recorded in [NOTICE](NOTICE).
