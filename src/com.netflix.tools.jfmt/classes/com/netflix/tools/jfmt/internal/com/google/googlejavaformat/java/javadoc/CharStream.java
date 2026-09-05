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

package com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.javadoc;

import static com.netflix.tools.jfmt.internal.com.google.common.base.Preconditions.checkArgument;
import static com.netflix.tools.jfmt.internal.com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * String reader designed for use from the lexer. Callers invoke the {@link #tryConsume tryConsume*}
 * methods to specify what characters they expect and then {@link #readAndResetRecorded} to retrieve
 * and consume the matched characters. This is a slightly odd API -- why not just return the matched
 * characters from tryConsume? -- but it is convenient for the lexer.
 *
 * <p>Tracks a position in the original input string rather than creating substrings, and reuses a
 * single {@link Matcher} instance per pattern to avoid allocation on every probe.
 */
final class CharStream {
  private final String input;
  private final Map<Pattern, Matcher> matchers = new HashMap<>();
  private int pos;
  private int toConsume;

  CharStream(String input) {
    this.input = checkNotNull(input);
  }

  boolean tryConsume(String expected) {
    if (!input.startsWith(expected, pos)) {
      return false;
    }
    toConsume = expected.length();
    return true;
  }

  /*
   * @param pattern the pattern to search for, which must be anchored to match only at position 0
   */
  boolean tryConsumeRegex(Pattern pattern) {
    Matcher matcher = matchers.computeIfAbsent(pattern, p -> p.matcher(input));
    matcher.region(pos, input.length());
    if (!matcher.find()) {
      return false;
    }
    checkArgument(matcher.start() == pos);
    toConsume = matcher.end() - pos;
    return true;
  }

  String readAndResetRecorded() {
    String result = input.substring(pos, pos + toConsume);
    pos += toConsume;
    toConsume = 0;
    return result;
  }

  boolean isExhausted() {
    return pos >= input.length();
  }
}
