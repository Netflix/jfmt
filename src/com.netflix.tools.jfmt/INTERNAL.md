# Integrated implementation sources

This module contains mechanically relocated upstream implementation sources. Their packages are rooted below `com.netflix.tools.jfmt.internal` so a linked jfmt module can coexist with versions selected by an application.

The integrated formatter implements one fixed OpenJDK-based dialect. Upstream Google and AOSP style-selection APIs are intentionally not part of the relocated implementation; jfmt-specific layout policy is applied directly to the formatting engine rather than presented as another selectable google-java-format style.

Commit `2b3ece6` is the unmodified source baseline for jfmt's formatter changes. Apart from package relocation, the baseline sources retain their upstream contents, copyright notices, and license headers. Dependency sources were selected from the static class dependency closure and verified by compiling the relocated closure. Subsequent changes apply jfmt's formatter patches directly to that baseline.

Upstream versions:

- google-java-format 1.35.0
- Guava 32.1.3-jre and FailureAccess 1.0.1
- Error Prone annotations 2.47.0
- Checker Framework qualifiers 3.37.0
- JSpecify 1.0.0
- JSR 305 3.0.2
- J2ObjC annotations 2.8
- AutoValue annotations 1.9
- AutoService annotations 1.0.1

The integrated sources retain their upstream license headers. The Checker Framework qualifiers are provided under the MIT License; the remaining integrated sources are provided under the Apache License 2.0.
