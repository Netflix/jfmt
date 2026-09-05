/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Input.Tok;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Input.Token;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;

import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.Trees.getEndPosition;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.Trees.getStartPosition;

/** Adds braces to non-empty control statement bodies. */
final class ControlBraces {

    private record Insertion(int position, String text) {}

    static String addBraces(String source) throws FormatterException {
        Context context = new Context();
        List<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>();
        JCCompilationUnit unit = Trees.parse(context, diagnostics, /* allowStringFolding= */ false, source);
        if (!diagnostics.isEmpty()) {
            throw FormatterException.fromJavacDiagnostics(diagnostics);
        }

        JavaInput input = new JavaInput(source);
        List<Insertion> insertions = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitIf(IfTree node, Void unused) {
                addBody(node.getThenStatement());
                StatementTree elseStatement = node.getElseStatement();
                if (elseStatement != null && !(elseStatement instanceof IfTree)) {
                    addBody(elseStatement);
                }
                return super.visitIf(node, unused);
            }

            @Override
            public Void visitForLoop(ForLoopTree node, Void unused) {
                addBody(node.getStatement());
                return super.visitForLoop(node, unused);
            }

            @Override
            public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void unused) {
                addBody(node.getStatement());
                return super.visitEnhancedForLoop(node, unused);
            }

            @Override
            public Void visitWhileLoop(WhileLoopTree node, Void unused) {
                addBody(node.getStatement());
                return super.visitWhileLoop(node, unused);
            }

            @Override
            public Void visitDoWhileLoop(DoWhileLoopTree node, Void unused) {
                addBody(node.getStatement());
                return super.visitDoWhileLoop(node, unused);
            }

            private void addBody(StatementTree body) {
                if (body instanceof BlockTree || body instanceof EmptyStatementTree) {
                    return;
                }
                int bodyStart = getStartPosition(body);
                int openingPosition = tokenEndBefore(input, bodyStart);
                insertions.add(new Insertion(openingPosition, " {"));
                insertions.add(closingBrace(source, getEndPosition(body, unit)));
            }
        }.scan(unit, null);

        if (insertions.isEmpty()) {
            return source;
        }
        insertions.sort(Comparator.comparingInt(Insertion::position)
                .reversed());
        StringBuilder result = new StringBuilder(source);
        for (Insertion insertion : insertions) {
            result.insert(insertion.position(), insertion.text());
        }
        return result.toString();
    }

    private static int tokenEndBefore(JavaInput input, int position) {
        int tokenEnd = -1;
        for (Token token : input.getTokens()) {
            Tok tok = token.getTok();
            if (tok.getPosition() >= position) {
                break;
            }
            if (!tok.getText().isEmpty()) {
                tokenEnd = tok.getPosition() + tok.length();
            }
        }
        if (tokenEnd < 0) {
            throw new IllegalStateException("control body has no preceding token");
        }
        return tokenEnd;
    }

    private static Insertion closingBrace(String source, int position) {
        int lineEnd = source.indexOf('\n', position);
        if (lineEnd < 0) {
            lineEnd = source.length();
        }
        if (!containsComment(source, position, lineEnd)) {
            return new Insertion(position, " }");
        }
        return lineEnd < source.length() ? new Insertion(lineEnd + 1, "}\n") : new Insertion(lineEnd, "\n}");
    }

    private static boolean containsComment(String source, int start, int end) {
        String text = source.substring(start, end);
        return text.contains("//") || text.contains("/*");
    }

    private ControlBraces() {}
}
