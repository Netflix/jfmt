/*
 * Copyright 2016 Google Inc.
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

package com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java;

import com.netflix.tools.jfmt.internal.com.google.auto.value.AutoBuilder;
import com.netflix.tools.jfmt.internal.com.google.errorprone.annotations.Immutable;

/** Options for jfmt's integrated google-java-format engine. */
@Immutable
public record JavaFormatterOptions(boolean formatJavadoc, boolean reorderModifiers) {

  /** Returns the multiplier for the unit of indent. */
  public int indentationMultiplier() {
    return 2;
  }

  int javadocWidth() {
    return 80;
  }

  boolean preserveJavadocWrapping() {
    return true;
  }

  boolean wrapLineComments() {
    return false;
  }

  /** Returns the default formatting options. */
  public static JavaFormatterOptions defaultOptions() {
    return builder().build();
  }

  /** Returns a builder for {@link JavaFormatterOptions}. */
  public static Builder builder() {
    return new AutoBuilder_JavaFormatterOptions_Builder()
        .formatJavadoc(true)
        .reorderModifiers(true);
  }

  /** A builder for {@link JavaFormatterOptions}. */
  @AutoBuilder
  public abstract static class Builder {

    public abstract Builder formatJavadoc(boolean formatJavadoc);

    public abstract Builder reorderModifiers(boolean reorderModifiers);

    public abstract JavaFormatterOptions build();
  }
}
