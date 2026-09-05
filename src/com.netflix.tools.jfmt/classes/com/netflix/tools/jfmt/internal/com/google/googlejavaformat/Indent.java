/*
 * Copyright 2015 Google Inc.
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

package com.netflix.tools.jfmt.internal.com.google.googlejavaformat;

import com.netflix.tools.jfmt.internal.com.google.common.base.MoreObjects;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Output.BreakTag;

/**
 * An indent for a {@link Doc.Level} or {@link Doc.Break}. The indent is either a constant {@code
 * int}, or a conditional expression whose value depends on whether or not a {@link Doc.Break} has
 * been broken.
 */
public abstract class Indent {

  abstract int eval(int parentIndent, int column);

  /** A constant function, returning a constant indent. */
  public static final class Const extends Indent {
    private final int n;

    public static final Const ZERO = new Const(+0);

    private Const(int n) {
      this.n = n;
    }

    public static Const make(int n, int indentMultiplier) {
      return new Const(n * indentMultiplier);
    }

    @Override
    int eval(int parentIndent, int column) {
      return n;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("n", n).toString();
    }
  }

  /** Add two relative indents. */
  public static Indent add(Indent first, Indent second) {
    return new Add(first, second);
  }

  private static final class Add extends Indent {
    private final Indent first;
    private final Indent second;

    private Add(Indent first, Indent second) {
      this.first = first;
      this.second = second;
    }

    @Override
    int eval(int parentIndent, int column) {
      return first.eval(parentIndent, column) + second.eval(parentIndent, column);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("first", first)
          .add("second", second)
          .toString();
    }
  }

  /** Continue at the indentation selected by an earlier break. */
  public static Indent fromBreak(BreakTag targetBreak) {
    return new FromBreak(targetBreak);
  }

  private static final class FromBreak extends Indent {
    private final BreakTag targetBreak;

    private FromBreak(BreakTag targetBreak) {
      this.targetBreak = targetBreak;
    }

    @Override
    int eval(int parentIndent, int column) {
      return targetBreak.indent().orElse(parentIndent) - parentIndent;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("targetBreak", targetBreak).toString();
    }
  }

  /** Aligns continuation lines with the current output column. */
  public static final class Align extends Indent {
    private final BreakTag targetBreak;
    private final int maximumIndent;
    private final Indent fallbackIndent;

    private Align(BreakTag targetBreak, int maximumIndent, Indent fallbackIndent) {
      this.targetBreak = targetBreak;
      this.maximumIndent = maximumIndent;
      this.fallbackIndent = fallbackIndent;
    }

    public static Align toCurrentColumn(int maximumIndent) {
      return new Align(null, maximumIndent, Const.ZERO);
    }

    public static Align toCurrentColumn(int maximumIndent, Indent fallbackIndent) {
      return new Align(null, maximumIndent, fallbackIndent);
    }

    public static Align toBreakColumn(
        BreakTag targetBreak, int maximumIndent, Indent fallbackIndent) {
      return new Align(targetBreak, maximumIndent, fallbackIndent);
    }

    @Override
    int eval(int parentIndent, int column) {
      int targetColumn = targetBreak == null ? column : targetBreak.column().orElse(column);
      int alignmentIndent = targetColumn - parentIndent;
      return alignmentIndent > maximumIndent
          ? fallbackIndent.eval(parentIndent, column)
          : alignmentIndent;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("targetBreak", targetBreak)
          .add("maximumIndent", maximumIndent)
          .add("fallbackIndent", fallbackIndent)
          .toString();
    }
  }

  /** A conditional function, whose value depends on whether a break was taken. */
  public static final class If extends Indent {
    private final BreakTag condition;
    private final Indent thenIndent;
    private final Indent elseIndent;

    private If(BreakTag condition, Indent thenIndent, Indent elseIndent) {
      this.condition = condition;
      this.thenIndent = thenIndent;
      this.elseIndent = elseIndent;
    }

    public static If make(BreakTag condition, Indent thenIndent, Indent elseIndent) {
      return new If(condition, thenIndent, elseIndent);
    }

    @Override
    int eval(int parentIndent, int column) {
      return (condition.wasBreakTaken() ? thenIndent : elseIndent).eval(parentIndent, column);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("condition", condition)
          .add("thenIndent", thenIndent)
          .add("elseIndent", elseIndent)
          .toString();
    }
  }
}
