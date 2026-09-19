# Integrated implementation sources

This module contains mechanically relocated upstream implementation sources. Their packages are rooted below `com.netflix.tools.jfmt.internal` so a linked jfmt module can coexist with versions selected by an application.

The integrated formatter implements one fixed OpenJDK-based dialect. Upstream Google and AOSP style-selection APIs are intentionally not part of the relocated implementation; jfmt-specific layout policy is applied directly to the formatting engine rather than presented as another selectable google-java-format style.

The formatter baseline and jfmt changes are kept separate and reproducible. [`vendor/google-java-format.sources`](../../vendor/google-java-format.sources) selects the upstream formatter source closure, while [`vendor/google-java-format.patch`](../../vendor/google-java-format.patch) contains the jfmt layout policy relative to the mechanically relocated upstream baseline. [`vendor/update-google-java-format`](../../vendor/update-google-java-format) reconstructs the integrated sources from published source artifacts and applies that patch, allowing the same changes to be rebased when the upstream baseline advances. Dependency sources were selected from the static class dependency closure and verified by compiling the relocated closure.

Upstream versions:

- google-java-format 1.36.1
- commonmark-java 0.28.0
- Guava 32.1.3-jre and FailureAccess 1.0.1
- Error Prone annotations 2.47.0
- Checker Framework qualifiers 3.37.0
- JSpecify 1.0.1
- JSR 305 3.0.2
- J2ObjC annotations 2.8
- AutoValue annotations 1.9
- AutoService annotations 1.0.1

The integrated sources retain their upstream license headers. CommonMark is provided under the BSD 2-Clause License and the Checker Framework qualifiers under the MIT License; the remaining integrated sources are provided under the Apache License 2.0.
