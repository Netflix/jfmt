package com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java;

import javax.annotation.processing.Generated;

@Generated("com.netflix.tools.jfmt.internal.com.google.auto.value.processor.AutoBuilderProcessor")
class AutoBuilder_JavaFormatterOptions_Builder extends JavaFormatterOptions.Builder {

  private Boolean formatJavadoc;

  private Boolean reorderModifiers;

  AutoBuilder_JavaFormatterOptions_Builder() {}

  @Override
  public JavaFormatterOptions.Builder formatJavadoc(boolean formatJavadoc) {
    this.formatJavadoc = formatJavadoc;
    return this;
  }

  @Override
  public JavaFormatterOptions.Builder reorderModifiers(boolean reorderModifiers) {
    this.reorderModifiers = reorderModifiers;
    return this;
  }

  @Override
  public JavaFormatterOptions build() {
    if (this.formatJavadoc == null || this.reorderModifiers == null) {
      StringBuilder missing = new StringBuilder();
      if (this.formatJavadoc == null) {
        missing.append(" formatJavadoc");
      }
      if (this.reorderModifiers == null) {
        missing.append(" reorderModifiers");
      }
      throw new IllegalStateException("Missing required properties:" + missing);
    }
    return new JavaFormatterOptions(this.formatJavadoc, this.reorderModifiers);
  }
}
