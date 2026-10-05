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

package com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java;

import static com.netflix.tools.jfmt.internal.com.google.common.collect.ImmutableList.toImmutableList;
import static com.netflix.tools.jfmt.internal.com.google.common.collect.Iterables.getLast;
import static com.netflix.tools.jfmt.internal.com.google.common.collect.Iterables.getOnlyElement;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Doc.FillMode.INDEPENDENT;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Doc.FillMode.UNIFIED;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Indent.If.make;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.OpsBuilder.BlankLineWanted.PRESERVE;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.OpsBuilder.BlankLineWanted.YES;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.Trees.getEndPosition;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.Trees.getMethodName;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.Trees.getSourceForNode;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.Trees.getStartPosition;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.Trees.operatorName;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.Trees.precedence;
import static com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.Trees.skipParen;
import static com.sun.source.tree.Tree.Kind.ANNOTATION;
import static com.sun.source.tree.Tree.Kind.ARRAY_ACCESS;
import static com.sun.source.tree.Tree.Kind.ASSIGNMENT;
import static com.sun.source.tree.Tree.Kind.BLOCK;
import static com.sun.source.tree.Tree.Kind.EXTENDS_WILDCARD;
import static com.sun.source.tree.Tree.Kind.IF;
import static com.sun.source.tree.Tree.Kind.METHOD_INVOCATION;
import static com.sun.source.tree.Tree.Kind.NEW_ARRAY;
import static com.sun.source.tree.Tree.Kind.NEW_CLASS;
import static com.sun.source.tree.Tree.Kind.STRING_LITERAL;
import static com.sun.source.tree.Tree.Kind.UNION_TYPE;
import static com.sun.source.tree.Tree.Kind.VARIABLE;
import static com.sun.tools.javac.code.Flags.COMPACT_RECORD_CONSTRUCTOR;
import static com.sun.tools.javac.code.Flags.IMPLICIT_CLASS;
import static com.sun.tools.javac.code.Flags.RECORD;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.netflix.tools.jfmt.internal.com.google.auto.value.AutoOneOf;
import com.netflix.tools.jfmt.internal.com.google.common.base.MoreObjects;
import com.netflix.tools.jfmt.internal.com.google.common.base.Throwables;
import com.netflix.tools.jfmt.internal.com.google.common.base.Verify;
import com.netflix.tools.jfmt.internal.com.google.common.collect.HashMultiset;
import com.netflix.tools.jfmt.internal.com.google.common.collect.ImmutableList;
import com.netflix.tools.jfmt.internal.com.google.common.collect.ImmutableMultimap;
import com.netflix.tools.jfmt.internal.com.google.common.collect.ImmutableSet;
import com.netflix.tools.jfmt.internal.com.google.common.collect.ImmutableSetMultimap;
import com.netflix.tools.jfmt.internal.com.google.common.collect.ImmutableSortedSet;
import com.netflix.tools.jfmt.internal.com.google.common.collect.Iterables;
import com.netflix.tools.jfmt.internal.com.google.common.collect.Iterators;
import com.netflix.tools.jfmt.internal.com.google.common.collect.Multiset;
import com.netflix.tools.jfmt.internal.com.google.common.collect.PeekingIterator;
import com.netflix.tools.jfmt.internal.com.google.common.collect.Range;
import com.netflix.tools.jfmt.internal.com.google.common.collect.RangeSet;
import com.netflix.tools.jfmt.internal.com.google.common.collect.Streams;
import com.netflix.tools.jfmt.internal.com.google.common.collect.TreeRangeSet;
import com.netflix.tools.jfmt.internal.com.google.errorprone.annotations.CheckReturnValue;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.CloseOp;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Doc;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Doc.FillMode;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.FormattingError;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Indent;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Input;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Newlines;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Op;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.OpenOp;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.OpsBuilder;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.OpsBuilder.BlankLineWanted;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.Output.BreakTag;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.DimensionHelpers.SortedDims;
import com.netflix.tools.jfmt.internal.com.google.googlejavaformat.java.DimensionHelpers.TypeWithDims;
import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CaseLabelTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.DefaultCaseLabelTree;
import com.sun.source.tree.DirectiveTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExportsTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.IntersectionTypeTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.OpensTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.PatternTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.ProvidesTree;
import com.sun.source.tree.RequiresTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.UsesTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.tree.YieldTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.TreeInfo;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.ToIntFunction;
import javax.lang.model.element.Name;
import com.netflix.tools.jfmt.internal.org.jspecify.annotations.Nullable;

/**
 * An AST visitor that builds a stream of {@link Op}s to format from the given {@link
 * CompilationUnitTree}.
 */
class JavaInputAstVisitor extends TreePathScanner<Void, Void> {

  /** Direction for Annotations (usually VERTICAL). */
  private enum Direction {
    VERTICAL,
    HORIZONTAL;

    boolean isVertical() {
      return this == VERTICAL;
    }
  }

  /** Whether to break or not. */
  private enum BreakOrNot {
    YES,
    NO;

    boolean isYes() {
      return this == YES;
    }
  }

  /** Whether to collapse empty blocks. */
  private enum CollapseEmptyOrNot {
    YES,
    NO;

    static CollapseEmptyOrNot valueOf(boolean b) {
      return b ? YES : NO;
    }

    boolean isYes() {
      return this == YES;
    }
  }

  /** Whether to allow leading blank lines in blocks. */
  private enum AllowLeadingBlankLine {
    YES,
    NO;
  }

  /** Whether to allow trailing blank lines in blocks. */
  private enum AllowTrailingBlankLine {
    YES,
    NO;

    static AllowTrailingBlankLine valueOf(boolean b) {
      return b ? YES : NO;
    }
  }

  /** Whether to include braces. */
  private enum BracesOrNot {
    YES,
    NO;

    boolean isYes() {
      return this == YES;
    }
  }

  /** Whether these declarations are the first in the block. */
  private enum FirstDeclarationsOrNot {
    YES,
    NO;

    boolean isYes() {
      return this == YES;
    }
  }

  // TODO(cushon): generalize this
  private static final ImmutableMultimap<String, String> TYPE_ANNOTATIONS = typeAnnotations();

  private static ImmutableSetMultimap<String, String> typeAnnotations() {
    ImmutableSetMultimap.Builder<String, String> result = ImmutableSetMultimap.builder();
    for (String annotation :
        ImmutableList.of(
            "com.netflix.tools.jfmt.internal.org.jspecify.annotations.NonNull",
            "com.netflix.tools.jfmt.internal.org.jspecify.annotations.Nullable",
            "com.netflix.tools.jfmt.internal.org.checkerframework.checker.nullness.qual.NonNull",
            "com.netflix.tools.jfmt.internal.org.checkerframework.checker.nullness.qual.Nullable")) {
      String simpleName = annotation.substring(annotation.lastIndexOf('.') + 1);
      result.put(simpleName, annotation);
    }
    return result.build();
  }

  protected final OpsBuilder builder;
  private final ImmutableSet.Builder<Integer> markdownJavadocPositions;
  private BreakTag argumentBreak;
  private BreakTag chainBreak;
  private ExpressionTree variableInitializer;
  // Flattened chains emit intermediate calls without scanning them, so TreePath is not their owner.
  private Tree argumentOwner;
  // Cache context-free measurements, not layout plans: enclosing breaks can change the chosen plan.
  private final Map<Tree, Integer> syntaxWidths = new IdentityHashMap<>();
  private final Map<List<? extends Tree>, RowWidthScores> rowScores = new IdentityHashMap<>();
  private LayoutContext layoutContext = LayoutContext.UNBROKEN;

  protected static final Indent.Const ZERO = Indent.Const.ZERO;
  protected final int indentMultiplier;
  private final int maximumAlignment;
  protected final Indent.Const minusTwo;
  protected final Indent.Const minusFour;
  protected final Indent.Const plusTwo;
  protected final Indent.Const plusFour;

  private final Set<Name> typeAnnotationSimpleNames = new HashSet<>();

  private static final ImmutableList<Op> breakList(Optional<BreakTag> breakTag) {
    return ImmutableList.of(Doc.Break.make(Doc.FillMode.UNIFIED, " ", ZERO, breakTag));
  }

  private static final ImmutableList<Op> breakFillList(Optional<BreakTag> breakTag) {
    return ImmutableList.of(
        OpenOp.make(ZERO),
        Doc.Break.make(Doc.FillMode.INDEPENDENT, " ", ZERO, breakTag),
        CloseOp.make());
  }

  private static final ImmutableList<Op> forceBreakList(Optional<BreakTag> breakTag) {
    return ImmutableList.of(Doc.Break.make(FillMode.FORCED, "", Indent.Const.ZERO, breakTag));
  }

  // Array elements shorter than this syntax width may use independent filling.
  private static final int MAX_ITEM_LENGTH_FOR_FILLING = 10;

  JavaInputAstVisitor(
      OpsBuilder builder,
      int indentMultiplier,
      ImmutableSet.Builder<Integer> markdownJavadocPositions) {
    this.builder = builder;
    this.indentMultiplier = indentMultiplier;
    this.markdownJavadocPositions = markdownJavadocPositions;
    // Beyond three continuation indents, use Sun's fixed continuation fallback.
    maximumAlignment = 3 * 4 * indentMultiplier;
    minusTwo = Indent.Const.make(-2, indentMultiplier);
    minusFour = Indent.Const.make(-4, indentMultiplier);
    plusTwo = Indent.Const.make(+2, indentMultiplier);
    plusFour = Indent.Const.make(+4, indentMultiplier);
  }

  // Structural budget for one row. Member weights count visible operations rather than every
  // descendant; enclosingExpressionComplexity separately accounts for nested calls and chains.
  private static final int MAX_UNBROKEN_COMPLEXITY = 6;

  // Compare weighted syntax width with the cost of another row. The last member's width is not
  // counted: it cannot separate any following member.
  private static final long ROW_BREAK_SCORE = 150;
  private static final long BROKEN_MEMBER_WEIGHT =
      ROW_BREAK_SCORE / (MAX_UNBROKEN_COMPLEXITY - 1);

  private enum ListLayout {
    FLAT,
    WRAPPED,
    BROKEN
  }

  private record ListStructure(
      ListLayout layout, int firstBreakIndex, boolean containsNestedBlock) {}

  private record ListPlan(ListLayout layout, int breakIndex, boolean ownsLayout) {}

  // List breaks separate siblings; chain breaks also replace optional wrapping inside their calls.
  private enum LayoutContext {
    UNBROKEN,
    BROKEN_LIST,
    BROKEN_CHAIN;

    LayoutContext inBrokenList() {
      // A list inside a broken chain must not re-enable optional child wrapping.
      return this == BROKEN_CHAIN ? BROKEN_CHAIN : BROKEN_LIST;
    }
  }

  // Cached positions and suffix weights let each continuation candidate be scored in constant time.
  private record RowWidthScores(long flatScore, long[] positions, long[] suffixWeights) {
    long wrappedScore(int breakIndex) {
      // A break moves every later member left by the first continuation member's position.
      return flatScore - positions[breakIndex] * suffixWeights[breakIndex];
    }

    int size() {
      return positions.length;
    }
  }

  private static ListLayout listLayout(
      int breakCount, int complexity, boolean breakAtTwoRows) {
    if (breakCount > 1
        || (breakAtTwoRows
            ? complexity >= 2 * MAX_UNBROKEN_COMPLEXITY
            : complexity > 2 * MAX_UNBROKEN_COMPLEXITY)) {
      return ListLayout.BROKEN;
    }
    return breakCount > 0 ? ListLayout.WRAPPED : ListLayout.FLAT;
  }

  private static int declarationStructure(MethodTree method) {
    int structure = 1 + typeStructure(method.getReturnType());
    for (TypeParameterTree typeParameter : method.getTypeParameters()) {
      structure += typeParameterStructure(typeParameter);
    }
    return structure;
  }

  private static int declarationStructure(ClassTree type) {
    int structure = 1;
    for (TypeParameterTree typeParameter : type.getTypeParameters()) {
      structure += typeParameterStructure(typeParameter);
    }
    return structure;
  }

  private static int typeParameterStructure(TypeParameterTree parameter) {
    int structure = 1;
    for (Tree bound : parameter.getBounds()) {
      structure += typeStructure(bound);
    }
    return structure;
  }

  private static int parameterStructure(VariableTree parameter) {
    return 1 + Math.min(2, nestedTypeStructure(parameter.getType()));
  }

  private ListLayout parameterListLayout(
      Optional<VariableTree> receiver,
      List<? extends VariableTree> parameters,
      int precedingStructure) {
    int totalComplexity = 0;
    int breakCount = 0;
    int remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
    int remainingStructure = MAX_UNBROKEN_COMPLEXITY - precedingStructure;
    boolean afterFirst = false;
    List<VariableTree> allParameters = new ArrayList<>();
    receiver.ifPresent(allParameters::add);
    allParameters.addAll(parameters);
    for (VariableTree parameter : allParameters) {
      int complexity = memberComplexity(parameter);
      int structure = parameterStructure(parameter);
      if (afterFirst
          && (complexity > remainingComplexity || structure > remainingStructure)) {
        breakCount++;
        remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
        remainingStructure = MAX_UNBROKEN_COMPLEXITY;
      }
      totalComplexity += complexity;
      remainingComplexity -= complexity;
      remainingStructure -= structure;
      afterFirst = true;
    }
    return listLayout(breakCount, totalComplexity, /* breakAtTwoRows= */ false);
  }

  private static int typeStructure(Tree type) {
    return type == null ? 0 : 1 + Math.min(2, nestedTypeStructure(type));
  }

  private static int nestedTypeStructure(Tree type) {
    return switch (type) {
      case AnnotatedTypeTree annotated -> nestedTypeStructure(annotated.getUnderlyingType());
      case ArrayTypeTree array -> nestedTypeStructure(array.getType());
      case ParameterizedTypeTree parameterized -> {
        int structure = 0;
        for (Tree argument : parameterized.getTypeArguments()) {
          structure += typeArgumentStructure(argument);
        }
        yield structure;
      }
      default -> 0;
    };
  }

  private static int typeArgumentStructure(Tree type) {
    return switch (type) {
      case AnnotatedTypeTree annotated -> typeArgumentStructure(annotated.getUnderlyingType());
      case ArrayTypeTree array -> typeArgumentStructure(array.getType());
      case ParameterizedTypeTree parameterized -> 1 + nestedTypeStructure(parameterized);
      case WildcardTree wildcard -> 1 + typeArgumentStructure(wildcard.getBound());
      case null, default -> 0;
    };
  }

  private static final com.sun.source.util.TreeScanner<Boolean, Void> BLOCK_FINDER =
      new com.sun.source.util.TreeScanner<>() {
        @Override
        public Boolean visitBlock(BlockTree block, Void unused) {
          return true;
        }

        @Override
        public Boolean reduce(Boolean first, Boolean second) {
          return Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second);
        }
      };

  private static boolean containsNestedBlock(ExpressionTree expression) {
    // Direct lambdas and anonymous classes handle their own bodies; look for blocks inside calls.
    return !(expression instanceof LambdaExpressionTree)
        && !(expression instanceof NewClassTree)
        && Boolean.TRUE.equals(BLOCK_FINDER.scan(expression, null));
  }

  private static Indent preservePreviousIndent(
      List<BreakTag> previousBreaks, Indent otherwise) {
    // Later continuation breaks reuse the most recent taken break instead of adding another indent.
    Indent indent = otherwise;
    for (BreakTag previous : previousBreaks) {
      indent = Indent.If.make(previous, Indent.fromBreak(previous), indent);
    }
    return indent;
  }

  private static int memberComplexity(Tree tree) {
    if (tree instanceof ParenthesizedTree parenthesized) {
      return memberComplexity(parenthesized.getExpression());
    }
    if (tree instanceof ConditionalExpressionTree conditional) {
      return conditionalMemberComplexity(conditional);
    }
    if (tree instanceof BinaryTree binary
        && (binary.getKind() == Tree.Kind.CONDITIONAL_AND
            || binary.getKind() == Tree.Kind.CONDITIONAL_OR)) {
      return logicalMemberComplexity(binary);
    }
    return 1 + Math.min(2, visibleOperations(tree));
  }

  private static int logicalMemberComplexity(BinaryTree binary) {
    List<ExpressionTree> operands = new ArrayList<>();
    walkInfix(precedence(binary), binary, operands, new ArrayList<>());
    int complexity = 0;
    for (ExpressionTree operand : operands) {
      complexity += memberComplexity(operand);
    }
    return complexity;
  }

  private static int conditionalMemberComplexity(ConditionalExpressionTree conditional) {
    return memberComplexity(conditional.getCondition())
        + memberComplexity(conditional.getTrueExpression())
        + memberComplexity(conditional.getFalseExpression());
  }

  private static int conditionalComplexity(ConditionalExpressionTree conditional) {
    return conditionalConditionComplexity(conditional.getCondition())
        + memberComplexity(conditional.getTrueExpression())
        + memberComplexity(conditional.getFalseExpression());
  }

  private static int conditionalConditionComplexity(ExpressionTree condition) {
    while (condition instanceof ParenthesizedTree parenthesized) {
      condition = parenthesized.getExpression();
    }
    if (!(condition instanceof BinaryTree binary)
        || (binary.getKind() != Tree.Kind.CONDITIONAL_AND
            && binary.getKind() != Tree.Kind.CONDITIONAL_OR)) {
      return memberComplexity(condition);
    }
    List<ExpressionTree> operands = new ArrayList<>();
    List<String> operators = new ArrayList<>();
    walkInfix(precedence(binary), binary, operands, operators);
    int complexity = operators.size();
    for (ExpressionTree operand : operands) {
      complexity += memberComplexity(operand);
    }
    return complexity;
  }

  private static int visibleOperations(Tree tree) {
    if (tree == null) {
      return 0;
    }
    return switch (tree) {
      case ParenthesizedTree parenthesized -> visibleOperations(parenthesized.getExpression());
      case ExpressionStatementTree statement -> visibleOperations(statement.getExpression());
      case VariableTree variable -> 1 + nestedOperation(variable.getInitializer());
      case AssignmentTree assignment -> 1 + nestedOperation(assignment.getExpression());
      case CompoundAssignmentTree assignment -> 1 + nestedOperation(assignment.getExpression());
      case BinaryTree binary ->
          1
              + logicalOperations(binary)
              + derivedValueOperations(binary.getLeftOperand())
              + derivedValueOperations(binary.getRightOperand());
      case UnaryTree unused -> 1;
      case MethodInvocationTree unused -> 1;
      case NewClassTree unused -> 1;
      case NewArrayTree unused -> 1;
      case MemberReferenceTree unused -> 1;
      case LambdaExpressionTree unused -> 1;
      case ConditionalExpressionTree unused -> 1;
      case SwitchExpressionTree unused -> 1;
      case TypeCastTree cast -> 1 + nestedOperation(cast.getExpression());
      case InstanceOfTree unused -> 1;
      default -> 0;
    };
  }

  private static int expressionComplexity(Tree tree) {
    if (tree == null) {
      return 0;
    }
    return switch (tree) {
      case ParenthesizedTree parenthesized -> expressionComplexity(parenthesized.getExpression());
      case ExpressionStatementTree statement -> expressionComplexity(statement.getExpression());
      case VariableTree variable -> 1 + expressionComplexity(variable.getInitializer());
      case AssignmentTree assignment -> 1 + expressionComplexity(assignment.getExpression());
      case CompoundAssignmentTree assignment ->
          1 + expressionComplexity(assignment.getExpression());
      case BinaryTree binary ->
          1
              + logicalOperations(binary)
              + Math.max(
                  nestedBinaryComplexity(binary.getLeftOperand()),
                  nestedBinaryComplexity(binary.getRightOperand()));
      case UnaryTree unary -> Math.max(1, expressionComplexity(unary.getExpression()));
      case MethodInvocationTree invocation ->
          1 + maximumExpressionComplexity(invocation.getArguments());
      case NewClassTree creation -> 1 + maximumExpressionComplexity(creation.getArguments());
      case NewArrayTree array -> 1 + maximumExpressionComplexity(array.getInitializers());
      case MemberReferenceTree unused -> 1;
      case LambdaExpressionTree lambda -> 1 + expressionComplexity(lambda.getBody());
      case ConditionalExpressionTree conditional ->
          1
              + Math.max(
                  expressionComplexity(conditional.getCondition()),
                  Math.max(
                      expressionComplexity(conditional.getTrueExpression()),
                      expressionComplexity(conditional.getFalseExpression())));
      case SwitchExpressionTree unused -> 1;
      case TypeCastTree cast -> expressionComplexity(cast.getExpression());
      case InstanceOfTree instanceOf -> 1 + expressionComplexity(instanceOf.getExpression());
      case BlockTree unused -> 1;
      default -> 0;
    };
  }

  private static int lambdaComplexity(LambdaExpressionTree lambda) {
    int bodyComplexity;
    if (lambda.getBody() instanceof BlockTree block) {
      bodyComplexity = 0;
      for (StatementTree statement : block.getStatements()) {
        bodyComplexity += memberComplexity(statement);
      }
    } else {
      bodyComplexity = expressionComplexity(lambda.getBody());
    }
    return lambda.getParameters().size() + 1 + bodyComplexity;
  }

  private static int caseRuleComplexity(CaseTree rule) {
    int complexity = 1;
    for (CaseLabelTree label : rule.getLabels()) {
      complexity += memberComplexity(label);
    }
    if (rule.getGuard() != null) {
      complexity += memberComplexity(rule.getGuard());
    }
    if (rule.getBody() instanceof ThrowTree thrown) {
      complexity += 1 + expressionComplexity(thrown.getExpression());
    } else if (rule.getBody() instanceof ExpressionTree expression) {
      complexity += expressionComplexity(expression);
    }
    return complexity;
  }

  private static int argumentComplexity(ExpressionTree argument) {
    while (argument instanceof ParenthesizedTree parenthesized) {
      argument = parenthesized.getExpression();
    }
    if (!(argument instanceof LambdaExpressionTree lambda)) {
      return Math.max(memberComplexity(argument), enclosingExpressionComplexity(argument));
    }
    if (lambda.getBody() instanceof BlockTree) {
      return Math.max(memberComplexity(argument), lambdaComplexity(lambda));
    }
    return Math.max(
        memberComplexity(argument),
        lambda.getParameters().size() + 1 + enclosingExpressionComplexity(lambda.getBody()));
  }

  private static int enclosingExpressionComplexity(Tree expression) {
    int complexity;
    if (expression instanceof NewArrayTree array && array.getInitializers() != null) {
      complexity = 1;
      for (ExpressionTree initializer : array.getInitializers()) {
        complexity += memberComplexity(initializer);
      }
    } else {
      complexity = expressionComplexity(expression);
      if (expression instanceof ExpressionTree expressionTree) {
        int callCount = chainCallCount(expressionTree);
        if (callCount > 1) {
          // One unit for the receiver and two per call, regardless of how the receiver is written.
          complexity = Math.max(complexity, 1 + 2 * callCount);
        }
      }
    }
    // expressionComplexity deliberately keeps only the deepest argument. Preserve sibling
    // structured arguments that would otherwise disappear from the enclosing decision.
    List<? extends ExpressionTree> arguments = switch (expression) {
      case MethodInvocationTree invocation -> invocation.getArguments();
      case NewClassTree construction -> construction.getArguments();
      default -> List.of();
    };
    for (ExpressionTree argument : arguments) {
      complexity +=
          Math.max(0, enclosingExpressionComplexity(argument) - memberComplexity(argument));
    }
    long structuredArguments =
        arguments.stream().filter(argument -> memberComplexity(argument) > 1).count();
    if (structuredArguments > 1) {
      for (ExpressionTree argument : arguments) {
        if (memberComplexity(argument) > 1) {
          complexity += memberComplexity(argument);
        }
      }
    }
    return complexity;
  }

  private int syntaxWidth(Tree member) {
    // Raw source spans include authored whitespace and can change after the first formatting pass.
    // javac's rendering is stable across those changes; comments have a separate layout.
    return syntaxWidths.computeIfAbsent(member, tree -> tree.toString().length());
  }

  private static int memberLayoutWeight(Tree member) {
    // Treat a method reference as one member rather than counting its invocation syntax.
    return member instanceof MemberReferenceTree ? 1 : memberComplexity(member);
  }

  private RowWidthScores rowWidthScores(List<? extends Tree> members) {
    return rowScores.computeIfAbsent(members, this::measureRows);
  }

  private RowWidthScores measureRows(List<? extends Tree> members) {
    int size = members.size();
    long[] positions = new long[size];
    long[] suffixWeights = new long[size];
    long column = 0;
    long flatScore = 0;
    int index = 0;
    // javac's lists can be linked; iterate once rather than repeatedly indexing each candidate row.
    for (Tree member : members) {
      long weight = memberLayoutWeight(member);
      positions[index] = column;
      suffixWeights[index] = weight;
      flatScore += weight * column;
      column += syntaxWidth(member) + 2;
      index++;
    }
    for (int i = size - 2; i >= 0; i--) {
      suffixWeights[i] += suffixWeights[i + 1];
    }
    return new RowWidthScores(flatScore, positions, suffixWeights);
  }

  private long flatWidthScore(List<? extends Tree> members) {
    return rowWidthScores(members).flatScore();
  }

  private long wrappedWidthScore(List<? extends Tree> members, int breakIndex) {
    return rowWidthScores(members).wrappedScore(breakIndex);
  }

  private int preferredWrappedBreakIndex(List<? extends Tree> members) {
    return preferredWrappedBreakIndex(members, -1);
  }

  private int preferredWrappedBreakIndex(
      List<? extends Tree> members, int structuralBreakIndex) {
    if (structuralBreakIndex > 0
        && members.stream().anyMatch(JavaInputAstVisitor::requiresIndependentLayout)) {
      // Bodies need the structural separator; moving it can pack other members against their braces.
      return structuralBreakIndex;
    }
    int bestIndex = structuralBreakIndex > 0 ? structuralBreakIndex : 1;
    long lowestScore =
        structuralBreakIndex > 0
            ? wrappedWidthScore(members, structuralBreakIndex)
            : Long.MAX_VALUE;
    int size = rowWidthScores(members).size();
    for (int i = 1; i < size; i++) {
      long score = wrappedWidthScore(members, i);
      if (structuralBreakIndex > 0 && i != structuralBreakIndex) {
        score += ROW_BREAK_SCORE;
      }
      if (score < lowestScore) {
        lowestScore = score;
        bestIndex = i;
      }
    }
    return bestIndex;
  }

  private long wrappedLayoutScore(List<? extends Tree> members) {
    return ROW_BREAK_SCORE
        + wrappedWidthScore(members, preferredWrappedBreakIndex(members));
  }

  private static long brokenLayoutScore(List<? extends Tree> members) {
    long score = ROW_BREAK_SCORE;
    boolean afterFirst = false;
    for (Tree member : members) {
      if (afterFirst) {
        // Squared weights make full expansion more costly than a single wrap for structured peers.
        long weight = memberLayoutWeight(member);
        score += BROKEN_MEMBER_WEIGHT * weight * weight;
      }
      afterFirst = true;
    }
    return score;
  }

  private static boolean requiresIndependentLayout(Tree member) {
    return switch (member) {
      case LambdaExpressionTree lambda -> lambda.getBody() instanceof BlockTree;
      case NewClassTree creation -> creation.getClassBody() != null;
      case ExpressionTree expression -> containsNestedBlock(expression);
      default -> false;
    };
  }

  private ListLayout widthAwareListLayout(
      List<? extends Tree> members, ListLayout structuralLayout) {
    if (layoutContext == LayoutContext.BROKEN_CHAIN) {
      boolean requiresIndependentLayout =
          members.stream().anyMatch(JavaInputAstVisitor::requiresIndependentLayout);
      return structuralLayout == ListLayout.BROKEN || requiresIndependentLayout
          ? structuralLayout
          : ListLayout.FLAT;
    }
    if (members.size() < 2 || layoutContext == LayoutContext.BROKEN_LIST) {
      return structuralLayout;
    }
    return switch (structuralLayout) {
      case FLAT -> {
        if (wrappedLayoutScore(members) >= flatWidthScore(members)) {
          yield ListLayout.FLAT;
        }
        // With two members, put both on continuation lines instead of leaving the first attached
        // to the invocation.
        yield members.size() == 2 ? ListLayout.BROKEN : ListLayout.WRAPPED;
      }
      case WRAPPED ->
          brokenLayoutScore(members) < wrappedLayoutScore(members)
              ? ListLayout.BROKEN
              : ListLayout.WRAPPED;
      case BROKEN -> ListLayout.BROKEN;
    };
  }

  private static ListStructure listStructure(
      List<? extends ExpressionTree> members,
      ToIntFunction<ExpressionTree> weight,
      boolean separateNestedBlocks) {
    int totalComplexity = 0;
    int remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
    int breaks = 0;
    int firstBreakIndex = -1;
    boolean hasNestedBlock = false;
    int index = 0;
    for (ExpressionTree member : members) {
      int complexity = weight.applyAsInt(member);
      boolean nestedBlock = separateNestedBlocks && containsNestedBlock(member);
      hasNestedBlock |= nestedBlock;
      if (index > 0 && (nestedBlock || complexity > remainingComplexity)) {
        if (firstBreakIndex < 0) {
          firstBreakIndex = index;
        }
        breaks++;
        remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
      }
      totalComplexity += complexity;
      remainingComplexity -= complexity;
      index++;
    }
    return new ListStructure(
        listLayout(breaks, totalComplexity, /* breakAtTwoRows= */ true),
        firstBreakIndex,
        hasNestedBlock);
  }

  private ListPlan listPlan(
      List<? extends ExpressionTree> members, ListStructure structure) {
    // Choose before entering the child context; suppression there must not change the parent's plan.
    ListLayout layout = widthAwareListLayout(members, structure.layout());
    if (structure.containsNestedBlock()) {
      // These arguments already contain line breaks. Keep the outer list flat or wrapped rather
      // than letting block contents force every argument onto its own line.
      layout = layout == ListLayout.FLAT ? ListLayout.FLAT : ListLayout.WRAPPED;
    } else {
      layout = listLayoutWithChildren(members, layout);
    }
    int breakIndex = layout == ListLayout.WRAPPED
        ? preferredWrappedBreakIndex(members, structure.firstBreakIndex())
        : -1;
    return new ListPlan(
        layout, breakIndex, layout != ListLayout.FLAT || structure.containsNestedBlock());
  }

  private ListPlan argumentListPlan(List<? extends ExpressionTree> arguments) {
    return listPlan(arguments,
        listStructure(arguments, JavaInputAstVisitor::argumentComplexity,
            /* separateNestedBlocks= */ true));
  }

  private ListLayout argumentListLayout(List<? extends ExpressionTree> arguments) {
    return argumentListPlan(arguments).layout();
  }

  private ListLayout listLayoutWithChildren(
      List<? extends ExpressionTree> members, ListLayout layout) {
    ListLayout childLayout = ListLayout.FLAT;
    for (ExpressionTree member : members) {
      ListLayout candidate = expressionLayout(member);
      if (candidate.ordinal() > childLayout.ordinal()) {
        childLayout = candidate;
      }
    }
    if (childLayout == ListLayout.BROKEN
        || (childLayout == ListLayout.WRAPPED && members.size() > 1)) {
      return ListLayout.BROKEN;
    }
    return layout;
  }

  private ListLayout expressionLayout(ExpressionTree expression) {
    while (expression instanceof ParenthesizedTree parenthesized) {
      expression = parenthesized.getExpression();
    }
    return switch (expression) {
      case AssignmentTree assignment -> expressionLayout(assignment.getExpression());
      case AnnotationTree annotation -> annotationLayout(annotation);
      case MethodInvocationTree invocation -> argumentListLayout(invocation.getArguments());
      case NewClassTree construction -> argumentListLayout(construction.getArguments());
      case NewArrayTree array -> arrayInitializerLayout(array.getInitializers());
      case LambdaExpressionTree lambda when lambda.getBody() instanceof ExpressionTree body ->
          expressionLayout(body);
      default -> ListLayout.FLAT;
    };
  }

  private ListLayout annotationLayout(AnnotationTree annotation) {
    List<? extends ExpressionTree> arguments = annotation.getArguments();
    if (arguments.size() == 1 && !(arguments.get(0) instanceof AssignmentTree)) {
      return expressionLayout(arguments.get(0));
    }
    boolean breakMembers = argumentListLayout(arguments) != ListLayout.FLAT
        || (arguments.size() > 1
            && Iterables.any(arguments, JavaInputAstVisitor::isArrayValue));
    return breakMembers ? ListLayout.BROKEN : ListLayout.FLAT;
  }

  private ListPlan arrayInitializerPlan(List<? extends ExpressionTree> expressions) {
    return listPlan(expressions,
        listStructure(expressions, JavaInputAstVisitor::memberComplexity,
            /* separateNestedBlocks= */ false));
  }

  private ListLayout arrayInitializerLayout(List<? extends ExpressionTree> expressions) {
    return expressions == null ? ListLayout.FLAT : arrayInitializerPlan(expressions).layout();
  }

  private static int maximumExpressionComplexity(List<? extends Tree> trees) {
    int maximum = 0;
    if (trees != null) {
      for (Tree tree : trees) {
        maximum = Math.max(maximum, expressionComplexity(tree));
      }
    }
    return maximum;
  }

  private static int nestedBinaryComplexity(ExpressionTree expression) {
    while (expression instanceof ParenthesizedTree parenthesized) {
      expression = parenthesized.getExpression();
    }
    if (expression instanceof BinaryTree binary) {
      return Math.max(
          nestedBinaryComplexity(binary.getLeftOperand()),
          nestedBinaryComplexity(binary.getRightOperand()));
    }
    return expressionComplexity(expression);
  }

  private static int derivedValueOperations(ExpressionTree expression) {
    while (expression instanceof ParenthesizedTree parenthesized) {
      expression = parenthesized.getExpression();
    }
    return switch (expression) {
      case AssignmentTree unused -> 1;
      case CompoundAssignmentTree unused -> 1;
      case TypeCastTree cast -> derivedValueOperations(cast.getExpression());
      default -> 0;
    };
  }

  private static int logicalOperations(ExpressionTree expression) {
    while (expression instanceof ParenthesizedTree parenthesized) {
      expression = parenthesized.getExpression();
    }
    if (!(expression instanceof BinaryTree binary)
        || (binary.getKind() != Tree.Kind.CONDITIONAL_AND
            && binary.getKind() != Tree.Kind.CONDITIONAL_OR)) {
      return 0;
    }
    return 1
        + logicalOperations(binary.getLeftOperand())
        + logicalOperations(binary.getRightOperand());
  }

  private static int nestedOperation(ExpressionTree expression) {
    if (expression == null) {
      return 0;
    }
    while (expression instanceof ParenthesizedTree parenthesized) {
      expression = parenthesized.getExpression();
    }
    return switch (expression) {
      case MemberSelectTree unused -> 1;
      case ArrayAccessTree unused -> 1;
      case MethodInvocationTree unused -> 1;
      case NewClassTree unused -> 1;
      case NewArrayTree unused -> 1;
      case ConditionalExpressionTree unused -> 1;
      case SwitchExpressionTree unused -> 1;
      case LambdaExpressionTree unused -> 1;
      case BinaryTree binary -> logicalOperations(binary);
      case TypeCastTree cast -> nestedOperation(cast.getExpression());
      default -> 0;
    };
  }

  /** A record of whether we have visited into an expression. */
  private final Deque<Boolean> inExpression = new ArrayDeque<>(ImmutableList.of(false));

  private boolean inExpression() {
    return inExpression.peekLast();
  }

  @Override
  public Void scan(Tree tree, Void unused) {
    // Pre-visit AST for preview features, since com.sun.source.tree.AnyPattern can't be
    // accessed directly without --enable-preview.
    if (tree instanceof JCTree.JCAnyPattern jcAnyPattern) {
      visitJcAnyPattern(jcAnyPattern);
      return null;
    }
    inExpression.addLast(tree instanceof ExpressionTree || inExpression.peekLast());
    int previous = builder.depth();
    try {
      super.scan(tree, null);
    } catch (FormattingError e) {
      throw e;
    } catch (Throwable t) {
      throw new FormattingError(builder.diagnostic(Throwables.getStackTraceAsString(t)));
    } finally {
      inExpression.removeLast();
    }
    builder.checkClosed(previous);
    return null;
  }

  @Override
  public Void visitCompilationUnit(CompilationUnitTree node, Void unused) {
    recordMarkdownJavadocPosition(node);
    boolean afterFirstToken = false;
    if (node.getPackageName() != null) {
      markForPartialFormat();
      visitPackage(node.getPackageName(), node.getPackageAnnotations());
      builder.forcedBreak();
      afterFirstToken = true;
    }
    dropEmptyDeclarations();
    if (!node.getImports().isEmpty()) {
      if (afterFirstToken) {
        builder.blankLineWanted(BlankLineWanted.YES);
      }
      for (ImportTree importDeclaration : node.getImports()) {
        markForPartialFormat();
        builder.blankLineWanted(PRESERVE);
        scan(importDeclaration, null);
        builder.forcedBreak();
      }
      afterFirstToken = true;
    }
    dropEmptyDeclarations();
    for (Tree type : node.getTypeDecls()) {
      if (afterFirstToken) {
        builder.blankLineWanted(BlankLineWanted.YES);
      }
      markForPartialFormat();
      scan(type, null);
      builder.forcedBreak();
      afterFirstToken = true;
      dropEmptyDeclarations();
    }
    handleModule(afterFirstToken, node);
    // set a partial format marker at EOF to make sure we can format the entire file
    markForPartialFormat();
    return null;
  }

  private void handleModule(boolean afterFirstToken, CompilationUnitTree node) {
    ModuleTree module = node.getModule();
    if (module != null) {
      if (afterFirstToken) {
        builder.blankLineWanted(YES);
      }
      markForPartialFormat();
      visitModule(module, null);
      builder.forcedBreak();
    }
  }

  /** Skips over extra semicolons at the top-level, or in a class member declaration lists. */
  private void dropEmptyDeclarations() {
    if (builder.peekToken().equals(Optional.of(";"))) {
      while (builder.peekToken().equals(Optional.of(";"))) {
        builder.forcedBreak();
        markForPartialFormat();
        token(";");
      }
    }
  }

  private void recordMarkdownJavadocPosition(Tree tree) {
    OptionalInt javadocPosition = javadocPosition(tree);
    if (javadocPosition.isPresent()) {
      int pos = javadocPosition.getAsInt();
      if (builder.getInput().getText().startsWith("///", pos)) {
        markdownJavadocPositions.add(pos);
      }
    }
  }

  @Override
  public Void visitClass(ClassTree tree, Void unused) {
    if ((TreeInfo.flags((JCTree) tree) & IMPLICIT_CLASS) == IMPLICIT_CLASS) {
      visitImplicitClass(tree);
      return null;
    }
    recordMarkdownJavadocPosition(tree);
    switch (tree.getKind()) {
      case ANNOTATION_TYPE -> visitAnnotationType(tree);
      case CLASS, INTERFACE -> visitClassDeclaration(tree);
      case ENUM -> visitEnumDeclaration(tree);
      case RECORD -> visitRecordDeclaration(tree);
      default -> throw new AssertionError(tree.getKind());
    }
    return null;
  }

  private void visitImplicitClass(ClassTree node) {
    builder.open(minusTwo);
    addBodyDeclarations(node.getMembers(), BracesOrNot.NO, FirstDeclarationsOrNot.YES);
    builder.close();
  }

  private void visitAnnotationType(ClassTree node) {
    sync(node);
    builder.open(ZERO);
    typeDeclarationModifiers(node.getModifiers());
    builder.open(ZERO);
    token("@");
    token("interface");
    builder.breakOp(" ");
    visit(node.getSimpleName());
    builder.close();
    builder.close();
    if (node.getMembers() == null) {
      builder.open(plusFour);
      token(";");
      builder.close();
    } else {
      addBodyDeclarations(node.getMembers(), BracesOrNot.YES, FirstDeclarationsOrNot.YES);
    }
    builder.guessToken(";");
  }

  @Override
  public Void visitArrayAccess(ArrayAccessTree node, Void unused) {
    sync(node);
    visitDot(node);
    return null;
  }

  @Override
  public Void visitNewArray(NewArrayTree node, Void unused) {
    if (node.getType() != null) {
      builder.open(plusFour);
      token("new");
      builder.space();

      TypeWithDims extractedDims = DimensionHelpers.extractDims(node.getType(), SortedDims.YES);
      Tree base = extractedDims.node();

      Deque<ExpressionTree> dimExpressions = new ArrayDeque<>(node.getDimensions());

      Deque<List<? extends AnnotationTree>> annotations = new ArrayDeque<>();
      annotations.add(ImmutableList.copyOf(node.getAnnotations()));
      annotations.addAll(node.getDimAnnotations());
      annotations.addAll(extractedDims.dims());

      scan(base, null);
      builder.open(ZERO);
      maybeAddDims(dimExpressions, annotations);
      builder.close();
      builder.close();
    }
    if (node.getInitializers() != null) {
      if (node.getType() != null) {
        builder.space();
      }
      visitArrayInitializer(node.getInitializers());
    }
    return null;
  }

  private boolean visitArrayInitializer(List<? extends ExpressionTree> expressions) {
    ListPlan plan = arrayInitializerPlan(expressions);
    LayoutContext previousContext = layoutContext;
    if (plan.ownsLayout()) {
      layoutContext = layoutContext.inBrokenList();
    }
    try {
      return emitArrayInitializer(expressions, plan);
    } finally {
      layoutContext = previousContext;
    }
  }

  private boolean emitArrayInitializer(
      List<? extends ExpressionTree> expressions, ListPlan plan) {
    int cols;
    if (expressions.isEmpty()) {
      tokenBreakTrailingComment("{", plusTwo);
      if (builder.peekToken().equals(Optional.of(","))) {
        token(",");
      }
      token("}", plusTwo);
    } else if (plan.layout() == ListLayout.BROKEN) {
      builder.open(plusTwo);
      token("{");
      builder.forcedBreak();
      boolean afterFirst = false;
      for (ExpressionTree expression : expressions) {
        if (afterFirst) {
          token(",");
          builder.forcedBreak();
        }
        scan(expression, null);
        afterFirst = true;
      }
      builder.guessToken(",");
      builder.forcedBreak(minusTwo);
      builder.close();
      token("}", plusTwo);
    } else if ((cols = argumentsAreTabular(expressions)) != -1) {
      builder.open(plusTwo);
      token("{");
      builder.forcedBreak();
      boolean afterFirstToken = false;
      for (Iterable<? extends ExpressionTree> row : Iterables.partition(expressions, cols)) {
        if (afterFirstToken) {
          builder.forcedBreak();
        }
        builder.open(row.iterator().next().getKind() == NEW_ARRAY || cols == 1 ? ZERO : plusFour);
        boolean firstInRow = true;
        for (ExpressionTree item : row) {
          if (!firstInRow) {
            token(",");
            builder.breakToFill(" ");
          }
          scan(item, null);
          firstInRow = false;
        }
        builder.guessToken(",");
        builder.close();
        afterFirstToken = true;
      }
      builder.breakOp(minusTwo);
      builder.close();
      token("}", plusTwo);
    } else {
      boolean inMemberValuePair = false;
      // Annotation assignments are emitted directly, so the path may omit the AssignmentTree.
      TreePath path = getCurrentPath();
      for (int i = 0; i < 2; i++) {
        if (path == null) {
          break;
        }
        if (path.getLeaf().getKind() == ANNOTATION) {
          inMemberValuePair = true;
          break;
        }
        path = path.getParentPath();
      }
      boolean shortItems = hasOnlyShortItems(expressions);
      boolean allowFilledElementsOnOwnLine = shortItems || !inMemberValuePair;
      boolean breakInitializer = plan.layout() != ListLayout.FLAT;

      builder.open(plusTwo);
      tokenBreakTrailingComment("{", plusTwo);
      boolean hasTrailingComma = hasTrailingToken(builder.getInput(), expressions, ",");
      builder.breakOp(hasTrailingComma ? FillMode.FORCED : FillMode.UNIFIED, "", ZERO);
      if (allowFilledElementsOnOwnLine) {
        builder.open(ZERO);
      }
      boolean afterFirstToken = false;
      FillMode fillMode = shortItems ? FillMode.INDEPENDENT : FillMode.UNIFIED;
      int index = 0;
      for (ExpressionTree expression : expressions) {
        if (afterFirstToken) {
          token(",");
          if (index == plan.breakIndex()) {
            builder.forcedBreak(plusFour);
          } else {
            builder.breakOp(fillMode, " ", ZERO);
          }
        }
        scan(expression, null);
        afterFirstToken = true;
        index++;
      }
      builder.guessToken(",");
      if (allowFilledElementsOnOwnLine) {
        builder.close();
      }
      if (!breakInitializer) {
        builder.breakOp(minusTwo);
      }
      builder.close();
      token("}", plusTwo);
    }
    return false;
  }

  private boolean hasOnlyShortItems(List<? extends ExpressionTree> expressions) {
    for (ExpressionTree expression : expressions) {
      if (syntaxWidth(expression) >= MAX_ITEM_LENGTH_FOR_FILLING) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Void visitArrayType(ArrayTypeTree node, Void unused) {
    sync(node);
    visitAnnotatedArrayType(node);
    return null;
  }

  private void visitAnnotatedArrayType(Tree node) {
    TypeWithDims extractedDims = DimensionHelpers.extractDims(node, SortedDims.YES);
    builder.open(plusFour);
    scan(extractedDims.node(), null);
    Deque<List<? extends AnnotationTree>> dims = new ArrayDeque<>(extractedDims.dims());
    maybeAddDims(dims);
    Verify.verify(dims.isEmpty());
    builder.close();
  }

  @Override
  public Void visitAssert(AssertTree node, Void unused) {
    sync(node);
    builder.open(ZERO);
    token("assert");
    builder.space();
    builder.open(node.getDetail() == null ? ZERO : plusFour);
    scan(node.getCondition(), null);
    if (node.getDetail() != null) {
      builder.breakOp(" ");
      token(":");
      builder.space();
      scan(node.getDetail(), null);
    }
    builder.close();
    builder.close();
    token(";");
    return null;
  }

  @Override
  public Void visitAssignment(AssignmentTree node, Void unused) {
    sync(node);
    boolean structuredExpression =
        node.getExpression() instanceof ConditionalExpressionTree
            || node.getExpression() instanceof BinaryTree
            || node.getExpression() instanceof MethodInvocationTree;
    builder.open(structuredExpression ? ZERO : plusFour);
    scan(node.getVariable(), null);
    builder.space();
    splitToken(operatorName(node));
    if (structuredExpression) {
      builder.space();
    } else {
      builder.breakOp(" ");
    }
    scan(node.getExpression(), null);
    builder.close();
    return null;
  }

  @Override
  public Void visitBlock(BlockTree node, Void unused) {
    visitBlock(node, CollapseEmptyOrNot.NO, AllowLeadingBlankLine.NO, AllowTrailingBlankLine.NO);
    return null;
  }

  @Override
  public Void visitCompoundAssignment(CompoundAssignmentTree node, Void unused) {
    sync(node);
    builder.open(plusFour);
    scan(node.getVariable(), null);
    builder.space();
    splitToken(operatorName(node));
    builder.breakOp(" ");
    scan(node.getExpression(), null);
    builder.close();
    return null;
  }

  @Override
  public Void visitBreak(BreakTree node, Void unused) {
    sync(node);
    builder.open(plusFour);
    token("break");
    if (node.getLabel() != null) {
      builder.breakOp(" ");
      visit(node.getLabel());
    }
    builder.close();
    token(";");
    return null;
  }

  @Override
  public Void visitTypeCast(TypeCastTree node, Void unused) {
    sync(node);
    builder.open(plusFour);
    token("(");
    scan(node.getType(), null);
    token(")");
    builder.breakOp(" ");
    scan(node.getExpression(), null);
    builder.close();
    return null;
  }

  @Override
  public Void visitNewClass(NewClassTree node, Void unused) {
    sync(node);
    builder.open(ZERO);
    if (node.getEnclosingExpression() != null) {
      scan(node.getEnclosingExpression(), null);
      builder.breakOp();
      token(".");
    }
    token("new");
    builder.space();
    addTypeArguments(node.getTypeArguments(), plusFour);
    if (node.getClassBody() != null) {
      List<AnnotationTree> annotations =
          visitModifiers(
              node.getClassBody().getModifiers(), Direction.HORIZONTAL, Optional.empty());
      visitAnnotations(annotations, BreakOrNot.NO, BreakOrNot.YES);
    }
    scan(node.getIdentifier(), null);
    addArguments(node, node.getArguments(), plusFour);
    builder.close();
    if (node.getClassBody() != null) {
      addBodyDeclarations(
          node.getClassBody().getMembers(), BracesOrNot.YES, FirstDeclarationsOrNot.YES);
    }
    return null;
  }

  @Override
  public Void visitConditionalExpression(ConditionalExpressionTree node, Void unused) {
    sync(node);
    int complexity = conditionalComplexity(node);
    boolean forceClauseBreaks = complexity > MAX_UNBROKEN_COMPLEXITY;

    builder.open(plusFour);
    scan(node.getCondition(), null);
    if (forceClauseBreaks) {
      builder.forcedBreak();
    } else {
      builder.breakOp(" ");
    }
    token("?");
    builder.space();
    scan(node.getTrueExpression(), null);
    if (forceClauseBreaks) {
      builder.forcedBreak();
    } else {
      builder.breakOp(" ");
    }
    token(":");
    builder.space();
    scan(node.getFalseExpression(), null);
    builder.close();
    return null;
  }

  @Override
  public Void visitContinue(ContinueTree node, Void unused) {
    sync(node);
    builder.open(plusFour);
    token("continue");
    if (node.getLabel() != null) {
      builder.breakOp(" ");
      visit(node.getLabel());
    }
    token(";");
    builder.close();
    return null;
  }

  @Override
  public Void visitDoWhileLoop(DoWhileLoopTree node, Void unused) {
    sync(node);
    token("do");
    visitStatement(
        node.getStatement(),
        CollapseEmptyOrNot.YES,
        AllowLeadingBlankLine.YES,
        AllowTrailingBlankLine.YES);
    if (node.getStatement().getKind() == BLOCK) {
      builder.space();
    } else {
      builder.breakOp(" ");
    }
    token("while");
    builder.space();
    token("(");
    scan(skipParen(node.getCondition()), null);
    token(")");
    token(";");
    return null;
  }

  @Override
  public Void visitEmptyStatement(EmptyStatementTree node, Void unused) {
    sync(node);
    dropEmptyDeclarations();
    return null;
  }

  @Override
  public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void unused) {
    sync(node);
    builder.open(ZERO);
    token("for");
    builder.space();
    token("(");
    builder.open(ZERO);
    visitToDeclare(
        DeclarationKind.NONE,
        Direction.HORIZONTAL,
        node.getVariable(),
        Optional.of(node.getExpression()),
        ":",
        /* trailing= */ Optional.empty());
    builder.close();
    token(")");
    builder.close();
    visitStatement(
        node.getStatement(),
        CollapseEmptyOrNot.YES,
        AllowLeadingBlankLine.YES,
        AllowTrailingBlankLine.NO);
    return null;
  }

  private void visitEnumConstantDeclaration(VariableTree enumConstant) {
    recordMarkdownJavadocPosition(enumConstant);
    for (AnnotationTree annotation : enumConstant.getModifiers().getAnnotations()) {
      scan(annotation, null);
      builder.forcedBreak();
    }
    visit(enumConstant.getName());
    NewClassTree init = ((NewClassTree) enumConstant.getInitializer());
    if (init.getArguments().isEmpty()) {
      builder.guessToken("(");
      builder.guessToken(")");
    } else {
      addArguments(init, init.getArguments(), plusFour);
    }
    if (init.getClassBody() != null) {
      addBodyDeclarations(
          init.getClassBody().getMembers(), BracesOrNot.YES, FirstDeclarationsOrNot.YES);
    }
  }

  private boolean visitEnumDeclaration(ClassTree node) {
    sync(node);
    builder.open(ZERO);
    typeDeclarationModifiers(node.getModifiers());
    builder.open(plusFour);
    token("enum");
    builder.breakOp(" ");
    visit(node.getSimpleName());
    builder.close();
    builder.close();
    if (!node.getImplementsClause().isEmpty()) {
      builder.open(plusFour);
      builder.breakOp(" ");
      builder.open(plusFour);
      token("implements");
      builder.breakOp(" ");
      builder.open(ZERO);
      boolean afterFirstToken = false;
      for (Tree superInterfaceType : node.getImplementsClause()) {
        if (afterFirstToken) {
          token(",");
          builder.breakToFill(" ");
        }
        scan(superInterfaceType, null);
        afterFirstToken = true;
      }
      builder.close();
      builder.close();
      builder.close();
    }
    builder.space();
    tokenBreakTrailingComment("{", plusTwo);
    ArrayList<VariableTree> enumConstants = new ArrayList<>();
    ArrayList<Tree> members = new ArrayList<>();
    for (Tree member : node.getMembers()) {
      if (member instanceof JCTree.JCVariableDecl variableDecl) {

        if ((variableDecl.mods.flags & Flags.ENUM) == Flags.ENUM) {
          enumConstants.add(variableDecl);
          continue;
        }
      }
      members.add(member);
    }
    if (enumConstants.isEmpty() && members.isEmpty()) {
      if (builder.peekToken().equals(Optional.of(";"))) {
        builder.open(plusTwo);
        builder.forcedBreak();
        token(";");
        builder.forcedBreak();
        dropEmptyDeclarations();
        builder.close();
        builder.open(ZERO);
        builder.forcedBreak();
        builder.blankLineWanted(BlankLineWanted.NO);
        token("}", plusTwo);
        builder.close();
      } else {
        builder.open(ZERO);
        builder.blankLineWanted(BlankLineWanted.NO);
        token("}");
        builder.close();
      }
    } else {
      builder.open(plusTwo);
      builder.blankLineWanted(BlankLineWanted.NO);
      builder.forcedBreak();
      builder.open(ZERO);
      boolean afterFirstToken = false;
      for (VariableTree enumConstant : enumConstants) {
        if (afterFirstToken) {
          token(",");
          builder.forcedBreak();
          builder.blankLineWanted(BlankLineWanted.PRESERVE);
        }
        markForPartialFormat();
        visitEnumConstantDeclaration(enumConstant);
        afterFirstToken = true;
      }
      if (builder.peekToken().orElse("").equals(",")) {
        token(",");
        builder.forcedBreak(); // The ";" goes on its own line.
      }
      builder.close();
      builder.close();
      if (builder.peekToken().equals(Optional.of(";"))) {
        builder.open(plusTwo);
        token(";");
        builder.forcedBreak();
        dropEmptyDeclarations();
        builder.close();
      }
      builder.open(ZERO);
      addBodyDeclarations(members, BracesOrNot.NO, FirstDeclarationsOrNot.NO);
      builder.forcedBreak();
      builder.blankLineWanted(BlankLineWanted.NO);
      token("}", plusTwo);
      builder.close();
    }
    builder.guessToken(";");
    return false;
  }

  private void visitRecordDeclaration(ClassTree node) {
    sync(node);
    typeDeclarationModifiers(node.getModifiers());
    Verify.verify(node.getExtendsClause() == null);
    boolean hasSuperInterfaceTypes = !node.getImplementsClause().isEmpty();
    token("record");
    builder.space();
    visit(node.getSimpleName());
    if (!node.getTypeParameters().isEmpty()) {
      token("<");
    }
    builder.open(plusFour);
    {
      if (!node.getTypeParameters().isEmpty()) {
        typeParametersRest(node.getTypeParameters(), hasSuperInterfaceTypes ? plusFour : ZERO);
      }
      ImmutableList<JCTree.JCVariableDecl> parameters = JavaInputAstVisitor.recordVariables(node);
      token("(");
      if (!parameters.isEmpty()) {
        // record headers can't declare receiver parameters
        visitAlignedFormals(
            /* receiver= */ Optional.empty(), parameters, declarationStructure(node));
      }
      token(")");
      if (hasSuperInterfaceTypes) {
        builder.breakToFill(" ");
        builder.open(node.getImplementsClause().size() > 1 ? plusFour : ZERO);
        token("implements");
        builder.space();
        boolean afterFirstToken = false;
        for (Tree superInterfaceType : node.getImplementsClause()) {
          if (afterFirstToken) {
            token(",");
            builder.breakOp(" ");
          }
          scan(superInterfaceType, null);
          afterFirstToken = true;
        }
        builder.close();
      }
    }
    builder.close();
    if (node.getMembers() == null) {
      token(";");
    } else {
      ImmutableList<Tree> members =
          node.getMembers().stream()
              .filter(t -> (TreeInfo.flags((JCTree) t) & Flags.GENERATED_MEMBER) == 0)
              .collect(toImmutableList());
      addBodyDeclarations(members, BracesOrNot.YES, FirstDeclarationsOrNot.YES);
    }
    dropEmptyDeclarations();
  }

  private static ImmutableList<JCTree.JCVariableDecl> recordVariables(ClassTree node) {
    return node.getMembers().stream()
        .filter(JCTree.JCVariableDecl.class::isInstance)
        .map(JCTree.JCVariableDecl.class::cast)
        .filter(m -> (m.mods.flags & RECORD) == RECORD)
        .collect(toImmutableList());
  }

  @Override
  public Void visitMemberReference(MemberReferenceTree node, Void unused) {
    builder.open(plusFour);
    scan(node.getQualifierExpression(), null);
    builder.breakOp();
    builder.op("::");
    addTypeArguments(node.getTypeArguments(), plusFour);
    switch (node.getMode()) {
      case INVOKE -> visit(node.getName());
      case NEW -> token("new");
    }
    builder.close();
    return null;
  }

  @Override
  public Void visitExpressionStatement(ExpressionStatementTree node, Void unused) {
    sync(node);
    scan(node.getExpression(), null);
    token(";");
    return null;
  }

  @Override
  public Void visitVariable(VariableTree node, Void unused) {
    sync(node);
    visitVariables(
        ImmutableList.of(node),
        DeclarationKind.NONE,
        variableAnnotationDirection(node.getModifiers()));
    return null;
  }

  private void visitVariables(
      List<VariableTree> fragments,
      DeclarationKind declarationKind,
      Direction annotationDirection) {
    if (fragments.size() == 1) {
      VariableTree fragment = fragments.get(0);
      if (declarationKind == DeclarationKind.FIELD) {
        recordMarkdownJavadocPosition(fragment);
      }
      declareOne(
          declarationKind,
          annotationDirection,
          Optional.of(fragment.getModifiers()),
          fragment.getType(),
          /* name= */ fragment.getName(),
          "",
          "=",
          Optional.ofNullable(fragment.getInitializer()),
          Optional.of(";"),
          /* receiverExpression= */ Optional.empty(),
          Optional.ofNullable(variableFragmentDims(false, 0, fragment.getType())));

    } else {
      declareMany(fragments, annotationDirection);
    }
  }

  private static TypeWithDims variableFragmentDims(
      boolean afterFirstToken, int leadingDims, Tree type) {
    if (type == null) {
      return null;
    }
    if (!afterFirstToken) {
      return DimensionHelpers.extractDims(type, SortedDims.YES);
    }
    TypeWithDims dims = DimensionHelpers.extractDims(type, SortedDims.NO);
    return new TypeWithDims(
        null,
        leadingDims > 0 ? dims.dims().subList(0, dims.dims().size() - leadingDims) : dims.dims());
  }

  @Override
  public Void visitForLoop(ForLoopTree node, Void unused) {
    sync(node);
    int complexity = 0;
    for (StatementTree initializer : node.getInitializer()) {
      complexity += memberComplexity(initializer);
    }
    if (node.getCondition() != null) {
      complexity += memberComplexity(node.getCondition());
    }
    for (ExpressionStatementTree update : node.getUpdate()) {
      complexity += memberComplexity(update);
    }
    boolean forceClauseBreaks = complexity > MAX_UNBROKEN_COMPLEXITY;

    token("for");
    builder.space();
    token("(");
    builder.open(Indent.Align.toCurrentColumn(maximumAlignment, plusFour));
    builder.breakOp(Doc.FillMode.INDEPENDENT, "", ZERO);
    builder.open(
        node.getInitializer().size() > 1
                && node.getInitializer().get(0).getKind() == Tree.Kind.EXPRESSION_STATEMENT
            ? plusFour
            : ZERO);
    if (!node.getInitializer().isEmpty()) {
      if (node.getInitializer().get(0).getKind() == VARIABLE) {
        PeekingIterator<StatementTree> it =
            Iterators.peekingIterator(node.getInitializer().iterator());
        visitVariables(
            variableFragments(it, it.next()), DeclarationKind.NONE, Direction.HORIZONTAL);
      } else {
        boolean afterFirstToken = false;
        builder.open(ZERO);
        for (StatementTree t : node.getInitializer()) {
          if (afterFirstToken) {
            token(",");
            builder.breakOp(" ");
          }
          scan(((ExpressionStatementTree) t).getExpression(), null);
          afterFirstToken = true;
        }
        token(";");
        builder.close();
      }
    } else {
      token(";");
    }
    builder.close();
    if (forceClauseBreaks) {
      builder.forcedBreak();
    } else {
      builder.breakOp(" ");
    }
    if (node.getCondition() != null) {
      scan(node.getCondition(), null);
    }
    token(";");
    if (!node.getUpdate().isEmpty()) {
      if (forceClauseBreaks) {
        builder.forcedBreak();
      } else {
        builder.breakOp(" ");
      }
      builder.open(node.getUpdate().size() <= 1 ? ZERO : plusFour);
      boolean firstUpdater = true;
      for (ExpressionStatementTree updater : node.getUpdate()) {
        if (!firstUpdater) {
          token(",");
          builder.breakToFill(" ");
        }
        scan(updater.getExpression(), null);
        firstUpdater = false;
      }
      builder.guessToken(";");
      builder.close();
    } else {
      builder.space();
    }
    builder.close();
    token(")");
    visitStatement(
        node.getStatement(),
        CollapseEmptyOrNot.YES,
        AllowLeadingBlankLine.YES,
        AllowTrailingBlankLine.NO);
    return null;
  }

  @Override
  public Void visitIf(IfTree node, Void unused) {
    sync(node);
    // Collapse chains of else-ifs.
    List<ExpressionTree> expressions = new ArrayList<>();
    List<StatementTree> statements = new ArrayList<>();
    while (true) {
      expressions.add(node.getCondition());
      statements.add(node.getThenStatement());
      if (node.getElseStatement() != null && node.getElseStatement().getKind() == IF) {
        node = (IfTree) node.getElseStatement();
      } else {
        break;
      }
    }
    builder.open(ZERO);
    boolean afterFirstToken = false;
    boolean followingBlock = false;
    int expressionsN = expressions.size();
    for (int i = 0; i < expressionsN; i++) {
      if (afterFirstToken) {
        if (followingBlock) {
          builder.space();
        } else {
          builder.forcedBreak();
        }
        token("else");
        builder.space();
      }
      token("if");
      builder.space();
      token("(");
      scan(skipParen(expressions.get(i)), null);
      token(")");
      // An empty block can collapse to "{}" if there are no if/else or else clauses
      boolean onlyClause = expressionsN == 1 && node.getElseStatement() == null;
      // Trailing blank lines are permitted if this isn't the last clause
      boolean trailingClauses = i < expressionsN - 1 || node.getElseStatement() != null;
      visitStatement(
          statements.get(i),
          CollapseEmptyOrNot.valueOf(onlyClause),
          AllowLeadingBlankLine.YES,
          AllowTrailingBlankLine.valueOf(trailingClauses));
      followingBlock = statements.get(i).getKind() == BLOCK;
      afterFirstToken = true;
    }
    if (node.getElseStatement() != null) {
      if (followingBlock) {
        builder.space();
      } else {
        builder.forcedBreak();
      }
      token("else");
      visitStatement(
          node.getElseStatement(),
          CollapseEmptyOrNot.NO,
          AllowLeadingBlankLine.YES,
          AllowTrailingBlankLine.NO);
    }
    builder.close();
    return null;
  }

  @Override
  public Void visitImport(ImportTree node, Void unused) {
    checkForTypeAnnotation(node);
    sync(node);
    token("import");
    builder.space();
    if (isModuleImport(node)) {
      token("module");
      builder.space();
    }
    if (node.isStatic()) {
      token("static");
      builder.space();
    }
    visitName(node.getQualifiedIdentifier());
    token(";");
    return null;
  }

  private static final @Nullable Method IS_MODULE_METHOD = getIsModuleMethod();

  private static @Nullable Method getIsModuleMethod() {
    try {
      return ImportTree.class.getMethod("isModule");
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private static boolean isModuleImport(ImportTree importTree) {
    if (IS_MODULE_METHOD == null) {
      return false;
    }
    try {
      return (boolean) IS_MODULE_METHOD.invoke(importTree);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  private void checkForTypeAnnotation(ImportTree node) {
    Name simpleName = getSimpleName(node);
    Collection<String> wellKnownAnnotations = TYPE_ANNOTATIONS.get(simpleName.toString());
    if (!wellKnownAnnotations.isEmpty()
        && wellKnownAnnotations.contains(node.getQualifiedIdentifier().toString())) {
      typeAnnotationSimpleNames.add(simpleName);
    }
  }

  private static Name getSimpleName(ImportTree importTree) {
    return switch (importTree.getQualifiedIdentifier()) {
      case IdentifierTree identifierTree -> identifierTree.getName();
      case MemberSelectTree memberSelectTree -> memberSelectTree.getIdentifier();
      case Tree tree -> throw new AssertionError(tree);
    };
  }

  @Override
  public Void visitBinary(BinaryTree node, Void unused) {
    sync(node);
    /*
     * Collect together all operators with same precedence to clean up indentation.
     */
    List<ExpressionTree> operands = new ArrayList<>();
    List<String> operators = new ArrayList<>();
    walkInfix(precedence(node), node, operands, operators);
    int complexity = 0;
    for (ExpressionTree operand : operands) {
      complexity += memberComplexity(operand);
    }
    boolean forceOperatorBreaks = complexity > MAX_UNBROKEN_COMPLEXITY;
    FillMode fillMode = hasOnlyShortItems(operands) ? INDEPENDENT : UNIFIED;
    TreePath parentPath = getCurrentPath().getParentPath();
    boolean assignmentExpression =
        parentPath != null
            && parentPath.getLeaf() instanceof AssignmentTree assignment
            && assignment.getExpression() == node;
    builder.open(assignmentExpression ? ZERO : plusFour);
    BreakTag expressionStart = assignmentExpression ? genSym() : null;
    if (expressionStart != null) {
      builder.breakOp(
          INDEPENDENT,
          "",
          Indent.Align.toCurrentColumn(maximumAlignment, plusFour),
          Optional.of(expressionStart));
    }
    scan(operands.get(0), null);
    int operatorsN = operators.size();
    for (int i = 0; i < operatorsN; i++) {
      builder.breakOp(
          forceOperatorBreaks ? FillMode.FORCED : fillMode,
          " ",
          expressionStart == null
              ? ZERO
              : Indent.Align.toBreakColumn(expressionStart, maximumAlignment, plusFour));
      builder.op(operators.get(i));
      builder.space();
      scan(operands.get(i + 1), null);
    }
    builder.close();
    return null;
  }

  @Override
  public Void visitInstanceOf(InstanceOfTree node, Void unused) {
    sync(node);
    builder.open(plusFour);
    scan(node.getExpression(), null);
    if (node.getPattern() != null) {
      builder.space();
    } else {
      builder.breakToFill(" ");
    }
    builder.open(ZERO);
    token("instanceof");
    if (node.getPattern() != null) {
      builder.space();
      scan(node.getPattern(), null);
    } else {
      builder.breakToFill(" ");
      scan(node.getType(), null);
    }
    builder.close();
    builder.close();
    return null;
  }

  @Override
  public Void visitIntersectionType(IntersectionTypeTree node, Void unused) {
    sync(node);
    builder.open(plusFour);
    boolean afterFirstToken = false;
    for (Tree type : node.getBounds()) {
      if (afterFirstToken) {
        builder.breakToFill(" ");
        token("&");
        builder.space();
      }
      scan(type, null);
      afterFirstToken = true;
    }
    builder.close();
    return null;
  }

  @Override
  public Void visitLabeledStatement(LabeledStatementTree node, Void unused) {
    sync(node);
    builder.open(ZERO);
    visit(node.getLabel());
    token(":");
    builder.forcedBreak();
    builder.close();
    scan(node.getStatement(), null);
    return null;
  }

  @Override
  public Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
    sync(node);
    boolean statementBody = node.getBodyKind() == LambdaExpressionTree.BodyKind.STATEMENT;
    boolean parens = builder.peekToken().equals(Optional.of("("));
    builder.open(parens ? plusFour : ZERO);
    if (parens) {
      token("(");
    }
    boolean afterFirstToken = false;
    for (VariableTree parameter : node.getParameters()) {
      if (afterFirstToken) {
        token(",");
        builder.breakOp(" ");
      }
      visitVariables(
          ImmutableList.of(parameter),
          DeclarationKind.NONE,
          variableAnnotationDirection(parameter.getModifiers()));
      afterFirstToken = true;
    }
    if (parens) {
      token(")");
    }
    builder.close();
    builder.space();
    builder.op("->");
    boolean breakExpressionBody =
        !statementBody && lambdaComplexity(node) > MAX_UNBROKEN_COMPLEXITY;
    Indent expressionBodyIndent =
        chainBreak == null ? ZERO : Indent.If.make(chainBreak, plusFour, ZERO);
    if (argumentBreak != null) {
      expressionBodyIndent = Indent.If.make(argumentBreak, plusFour, expressionBodyIndent);
    }
    Indent structuralBodyIndent = ZERO;
    if (chainBreak != null) {
      structuralBodyIndent =
          Indent.If.make(chainBreak, Indent.fromBreak(chainBreak), structuralBodyIndent);
    }
    if (argumentBreak != null) {
      structuralBodyIndent =
          Indent.If.make(argumentBreak, Indent.fromBreak(argumentBreak), structuralBodyIndent);
    }
    builder.open(
        statementBody
            ? ZERO
            : breakExpressionBody
                ? Indent.add(structuralBodyIndent, plusFour)
                : expressionBodyIndent);
    if (statementBody) {
      builder.space();
    } else if (breakExpressionBody) {
      builder.forcedBreak();
    } else {
      builder.breakOp(" ");
    }
    switch (node.getBody()) {
      case BlockTree blockTree ->
          visitBlock(
              blockTree,
              CollapseEmptyOrNot.YES,
              AllowLeadingBlankLine.NO,
              AllowTrailingBlankLine.NO);
      case Tree expressionTree -> scan(expressionTree, null);
    }
    builder.close();
    return null;
  }

  @Override
  public Void visitAnnotation(AnnotationTree node, Void unused) {
    sync(node);

    if (visitSingleMemberAnnotation(node)) {
      return null;
    }

    builder.open(ZERO);
    token("@");
    scan(node.getAnnotationType(), null);
    if (!node.getArguments().isEmpty()) {
      builder.open(plusFour);
      token("(");
      // Named members are configuration entries rather than positional arguments. Once the list
      // needs separation, expose each entry instead of packing continuation rows.
      boolean breakMembers = annotationLayout(node) != ListLayout.FLAT;
      LayoutContext previousContext = layoutContext;
      if (breakMembers) {
        builder.forcedBreak();
        layoutContext = layoutContext.inBrokenList();
      } else {
        builder.breakOp();
      }
      try {
        boolean afterFirstToken = false;
        for (ExpressionTree argument : node.getArguments()) {
          if (afterFirstToken) {
            token(",");
            if (breakMembers) {
              builder.forcedBreak();
            } else {
              builder.breakOp(" ");
            }
          }
          if (argument instanceof AssignmentTree assignment) {
            visitAnnotationArgument(assignment);
          } else {
            scan(argument, null);
          }
          afterFirstToken = true;
        }
      } finally {
        layoutContext = previousContext;
      }
      token(")");
      builder.close();
      builder.close();
      return null;

    } else if (builder.peekToken().equals(Optional.of("("))) {
      token("(");
      token(")");
    }
    builder.close();
    return null;
  }

  private static boolean isArrayValue(ExpressionTree argument) {
    if (!(argument instanceof AssignmentTree assignmentTree)) {
      return false;
    }
    ExpressionTree expression = assignmentTree.getExpression();
    return expression instanceof NewArrayTree newArrayTree && newArrayTree.getType() == null;
  }

  private void visitAnnotationArgument(AssignmentTree node) {
    boolean isArrayInitializer = node.getExpression().getKind() == NEW_ARRAY;
    sync(node);
    builder.open(isArrayInitializer ? ZERO : plusFour);
    scan(node.getVariable(), null);
    builder.space();
    token("=");
    if (isArrayInitializer) {
      builder.space();
    } else {
      builder.breakOp(" ");
    }
    scan(node.getExpression(), null);
    builder.close();
  }

  @Override
  public Void visitAnnotatedType(AnnotatedTypeTree node, Void unused) {
    sync(node);
    switch (node.getUnderlyingType()) {
      case MemberSelectTree selectTree -> {
        scan(selectTree.getExpression(), null);
        token(".");
        visitAnnotations(node.getAnnotations(), BreakOrNot.NO, BreakOrNot.NO);
        builder.breakToFill(" ");
        visit(selectTree.getIdentifier());
      }
      case ArrayTypeTree unusedTree -> visitAnnotatedArrayType(node);
      case ExpressionTree base -> {
        visitAnnotations(node.getAnnotations(), BreakOrNot.NO, BreakOrNot.NO);
        builder.breakToFill(" ");
        scan(base, null);
      }
    }
    return null;
  }

  @Override
  public Void visitMethod(MethodTree node, Void unused) {
    recordMarkdownJavadocPosition(node);
    sync(node);
    List<? extends AnnotationTree> annotations = node.getModifiers().getAnnotations();
    List<? extends AnnotationTree> returnTypeAnnotations = ImmutableList.of();

    boolean isRecordConstructor =
        (((JCMethodDecl) node).mods.flags & COMPACT_RECORD_CONSTRUCTOR)
            == COMPACT_RECORD_CONSTRUCTOR;

    if (!node.getTypeParameters().isEmpty() && !annotations.isEmpty()) {
      int typeParameterStart = getStartPosition(node.getTypeParameters().get(0));
      for (int i = 0; i < annotations.size(); i++) {
        if (getStartPosition(annotations.get(i)) > typeParameterStart) {
          returnTypeAnnotations = annotations.subList(i, annotations.size());
          annotations = annotations.subList(0, i);
          break;
        }
      }
    }
    List<AnnotationTree> typeAnnotations =
        visitModifiers(
            node.getModifiers(),
            annotations,
            Direction.VERTICAL,
            /* declarationAnnotationBreak= */ Optional.empty());
    if (node.getTypeParameters().isEmpty() && node.getReturnType() != null) {
      // If there are type parameters, we use a heuristic above to format annotations after the
      // type parameter declarations as type-use annotations. If there are no type parameters,
      // use the heuristics in visitModifiers for recognizing well known type-use annotations and
      // formatting them as annotations on the return type.
      returnTypeAnnotations = typeAnnotations;
      typeAnnotations = ImmutableList.of();
    }

    Tree baseReturnType = null;
    Deque<List<? extends AnnotationTree>> dims = null;
    if (node.getReturnType() != null) {
      TypeWithDims extractedDims =
          DimensionHelpers.extractDims(node.getReturnType(), SortedDims.YES);
      baseReturnType = extractedDims.node();
      dims = new ArrayDeque<>(extractedDims.dims());
    } else {
      verticalAnnotations(typeAnnotations);
      typeAnnotations = ImmutableList.of();
    }

    builder.open(plusFour);
    BreakTag breakBeforeName = genSym();
    BreakTag breakBeforeType = genSym();
    builder.open(ZERO);
    {
      boolean afterFirstToken = false;
      if (!typeAnnotations.isEmpty()) {
        visitAnnotations(typeAnnotations, BreakOrNot.NO, BreakOrNot.NO);
        afterFirstToken = true;
      }
      if (!node.getTypeParameters().isEmpty()) {
        if (afterFirstToken) {
          builder.breakToFill(" ");
        }
        token("<");
        typeParametersRest(node.getTypeParameters(), plusFour);
        afterFirstToken = true;
      }

      boolean openedNameAndTypeScope = false;
      // constructor-like declarations that don't match the name of the enclosing class are
      // parsed as method declarations with a null return type
      if (baseReturnType != null) {
        if (afterFirstToken) {
          builder.breakOp(INDEPENDENT, " ", ZERO, Optional.of(breakBeforeType));
        } else {
          afterFirstToken = true;
        }
        if (!openedNameAndTypeScope) {
          builder.open(make(breakBeforeType, plusFour, ZERO));
          openedNameAndTypeScope = true;
        }
        builder.open(ZERO);
        if (!returnTypeAnnotations.isEmpty()) {
          visitAnnotations(returnTypeAnnotations, BreakOrNot.NO, BreakOrNot.NO);
          builder.breakOp(" ");
        }
        scan(baseReturnType, null);
        maybeAddDims(dims);
        builder.close();
      }
      if (afterFirstToken) {
        builder.breakOp(Doc.FillMode.INDEPENDENT, " ", ZERO, Optional.of(breakBeforeName));
      } else {
        afterFirstToken = true;
      }
      if (!openedNameAndTypeScope) {
        builder.open(ZERO);
        openedNameAndTypeScope = true;
      }
      String name = node.getName().toString();
      if (name.equals("<init>")) {
        name = builder.peekToken().get();
      }
      token(name);
      if (!isRecordConstructor) {
        token("(");
      }
      // end of name and type scope
      builder.close();
    }
    builder.close();

    builder.open(Indent.If.make(breakBeforeName, plusFour, ZERO));
    builder.open(Indent.If.make(breakBeforeType, plusFour, ZERO));
    builder.open(ZERO);
    {
      if (!isRecordConstructor) {
        if (!node.getParameters().isEmpty() || node.getReceiverParameter() != null) {
          visitAlignedFormals(
              Optional.ofNullable(node.getReceiverParameter()),
              node.getParameters(),
              declarationStructure(node));
        }
        token(")");
      }
      if (dims != null) {
        maybeAddDims(dims);
      }
      if (!node.getThrows().isEmpty()) {
        int throwsComplexity = 0;
        for (ExpressionTree thrownType : node.getThrows()) {
          throwsComplexity += memberComplexity(thrownType);
        }
        if (throwsComplexity > MAX_UNBROKEN_COMPLEXITY) {
          builder.forcedBreak(plusFour);
        } else {
          builder.breakToFill(" ");
        }
        builder.open(plusFour);
        {
          visitThrowsClause(node.getThrows());
        }
        builder.close();
      }
      if (node.getDefaultValue() != null) {
        builder.space();
        token("default");
        if (node.getDefaultValue().getKind() == Tree.Kind.NEW_ARRAY) {
          builder.open(minusFour);
          {
            builder.space();
            scan(node.getDefaultValue(), null);
          }
          builder.close();
        } else {
          builder.open(ZERO);
          {
            builder.breakToFill(" ");
            scan(node.getDefaultValue(), null);
          }
          builder.close();
        }
      }
    }
    builder.close();
    builder.close();
    builder.close();
    if (node.getBody() == null) {
      token(";");
    } else {
      builder.space();
      builder.token("{", Doc.Token.RealOrImaginary.REAL, plusTwo, Optional.of(plusTwo));
    }
    builder.close();

    if (node.getBody() != null) {
      methodBody(node);
    }

    return null;
  }

  private void methodBody(MethodTree node) {
    if (node.getBody().getStatements().isEmpty()) {
      builder.blankLineWanted(BlankLineWanted.NO);
    } else {
      builder.open(plusTwo);
      builder.forcedBreak();
      builder.blankLineWanted(BlankLineWanted.PRESERVE);
      visitStatements(node.getBody().getStatements());
      builder.close();
      builder.forcedBreak();
      builder.blankLineWanted(BlankLineWanted.NO);
      markForPartialFormat();
    }
    token("}", plusTwo);
  }

  @Override
  public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
    sync(node);
    visitDot(node);
    return null;
  }

  @Override
  public Void visitMemberSelect(MemberSelectTree node, Void unused) {
    sync(node);
    visitDot(node);
    return null;
  }

  @Override
  public Void visitLiteral(LiteralTree node, Void unused) {
    sync(node);
    String sourceForNode = getSourceForNode(node, getCurrentPath());
    if (sourceForNode.startsWith("\"\"\"")) {
      String separator = Newlines.guessLineSeparator(sourceForNode);
      ImmutableList<String> initialLines = sourceForNode.lines().collect(toImmutableList());
      String stripped = initialLines.stream().skip(1).collect(joining(separator)).stripIndent();
      // Use the last line of the text block to determine if it is deindented to column 0, by
      // comparing the length of the line in the input source with the length after processing
      // the text block contents with stripIndent().
      boolean deindent =
          getLast(initialLines).stripTrailing().length()
              == Streams.findLast(stripped.lines()).orElseThrow().stripTrailing().length();
      if (deindent) {
        Indent indent = Indent.Const.make(Integer.MIN_VALUE / indentMultiplier, indentMultiplier);
        builder.breakOp(indent);
      }
      token(sourceForNode);
      return null;
    }
    if (isUnaryMinusLiteral(sourceForNode)) {
      token("-");
      sourceForNode = sourceForNode.substring(1).trim();
    }
    token(sourceForNode);
    return null;
  }

  // A negative numeric literal -n is usually represented as unary minus on n,
  // but that doesn't work for integer or long MIN_VALUE. The parser works
  // around that by representing it directly as a signed literal (with no
  // unary minus), but the lexer still expects two tokens.
  private static boolean isUnaryMinusLiteral(String literalTreeSource) {
    return literalTreeSource.startsWith("-");
  }

  private void visitPackage(
      ExpressionTree packageName, List<? extends AnnotationTree> packageAnnotations) {
    if (!packageAnnotations.isEmpty()) {
      for (AnnotationTree annotation : packageAnnotations) {
        builder.forcedBreak();
        scan(annotation, null);
      }
      builder.forcedBreak();
    }
    builder.open(plusFour);
    token("package");
    builder.space();
    visitName(packageName);
    builder.close();
    token(";");
  }

  @Override
  public Void visitParameterizedType(ParameterizedTypeTree node, Void unused) {
    sync(node);
    if (node.getTypeArguments().isEmpty()) {
      scan(node.getType(), null);
      token("<");
      token(">");
    } else {
      builder.open(plusFour);
      scan(node.getType(), null);
      token("<");
      builder.breakOp();
      builder.open(ZERO);
      boolean afterFirstToken = false;
      for (Tree typeArgument : node.getTypeArguments()) {
        if (afterFirstToken) {
          token(",");
          builder.breakOp(" ");
        }
        scan(typeArgument, null);
        afterFirstToken = true;
      }
      builder.close();
      builder.close();
      token(">");
    }
    return null;
  }

  @Override
  public Void visitParenthesized(ParenthesizedTree node, Void unused) {
    token("(");
    scan(node.getExpression(), null);
    token(")");
    return null;
  }

  @Override
  public Void visitUnary(UnaryTree node, Void unused) {
    sync(node);
    String operatorName = operatorName(node);
    if (((JCTree) node).getTag().isPostUnaryOp()) {
      scan(node.getExpression(), null);
      splitToken(operatorName);
    } else {
      splitToken(operatorName);
      if (ambiguousUnaryOperator(node, operatorName)) {
        builder.space();
      }
      scan(node.getExpression(), null);
    }
    return null;
  }

  private void splitToken(String operatorName) {
    for (int i = 0; i < operatorName.length(); i++) {
      token(String.valueOf(operatorName.charAt(i)));
    }
  }

  private boolean ambiguousUnaryOperator(UnaryTree node, String operatorName) {
    switch (node.getKind()) {
      case UNARY_MINUS, UNARY_PLUS -> {}
      default -> {
        return false;
      }
    }
    JCTree.Tag tag = unaryTag(node.getExpression());
    if (tag == null) {
      return false;
    }
    if (tag.isPostUnaryOp()) {
      return false;
    }
    if (!operatorName(node).startsWith(operatorName)) {
      return false;
    }
    return true;
  }

  private JCTree.Tag unaryTag(ExpressionTree expression) {
    return switch (expression) {
      case UnaryTree unary -> ((JCTree) unary).getTag();
      case LiteralTree literal
          when isUnaryMinusLiteral(getSourceForNode(literal, getCurrentPath())) ->
          JCTree.Tag.MINUS;
      default -> null;
    };
  }

  @Override
  public Void visitPrimitiveType(PrimitiveTypeTree node, Void unused) {
    sync(node);
    switch (node.getPrimitiveTypeKind()) {
      case BOOLEAN -> token("boolean");
      case BYTE -> token("byte");
      case SHORT -> token("short");
      case INT -> token("int");
      case LONG -> token("long");
      case CHAR -> token("char");
      case FLOAT -> token("float");
      case DOUBLE -> token("double");
      case VOID -> token("void");
      default -> throw new AssertionError(node.getPrimitiveTypeKind());
    }
    return null;
  }

  private boolean visit(Name name) {
    token(name.toString());
    return false;
  }

  @Override
  public Void visitReturn(ReturnTree node, Void unused) {
    sync(node);
    token("return");
    if (node.getExpression() != null) {
      builder.space();
      scan(node.getExpression(), null);
    }
    token(";");
    return null;
  }

  /** Emits a single unnamed value directly, leaving any nested list layout to the value. */
  private boolean visitSingleMemberAnnotation(AnnotationTree node) {
    if (node.getArguments().size() != 1) {
      return false;
    }
    ExpressionTree value = getOnlyElement(node.getArguments());
    if (value.getKind() == ASSIGNMENT) {
      return false;
    }
    boolean isArrayInitializer = value.getKind() == NEW_ARRAY;
    builder.open(isArrayInitializer ? ZERO : plusFour);
    token("@");
    scan(node.getAnnotationType(), null);
    token("(");
    if (!isArrayInitializer) {
      builder.breakOp();
    }
    scan(value, null);
    builder.close();
    token(")");
    return true;
  }

  @Override
  public Void visitCase(CaseTree node, Void unused) {
    sync(node);
    markForPartialFormat();
    builder.forcedBreak();
    List<? extends CaseLabelTree> labels = node.getLabels();
    boolean isDefault =
        labels.size() == 1 && getOnlyElement(labels).getKind().name().equals("DEFAULT_CASE_LABEL");
    builder.open(node.getCaseKind().equals(CaseTree.CaseKind.RULE) ? plusFour : ZERO);
    if (isDefault) {
      token("default", ZERO);
    } else {
      token("case", ZERO);
      builder.open(ZERO);
      builder.space();
      boolean afterFirstToken = false;
      for (Tree expression : labels) {
        if (afterFirstToken) {
          token(",");
          builder.breakOp(" ");
        }
        scan(expression, null);
        afterFirstToken = true;
      }
      builder.close();
    }

    final ExpressionTree guard = node.getGuard();
    if (guard != null) {
      builder.breakToFill(" ");
      token("when");
      builder.space();
      scan(guard, null);
    }

    switch (node.getCaseKind()) {
      case STATEMENT -> {
        token(":");
        builder.open(plusTwo);
        visitStatements(node.getStatements());
        builder.close();
        builder.close();
      }
      case RULE -> {
        builder.space();
        token("-");
        token(">");
        if (node.getBody().getKind() == BLOCK) {
          builder.close();
          builder.space();
          // Explicit call with {@link CollapseEmptyOrNot.YES} to handle empty case blocks.
          visitBlock(
              (BlockTree) node.getBody(),
              CollapseEmptyOrNot.YES,
              AllowLeadingBlankLine.NO,
              AllowTrailingBlankLine.NO);
        } else {
          boolean breakRuleBody = caseRuleComplexity(node) > MAX_UNBROKEN_COMPLEXITY;
          if (node.getBody() instanceof ExpressionTree expression) {
            breakRuleBody |= exceedsUnbrokenChainComplexity(expression);
          }
          if (breakRuleBody) {
            builder.forcedBreak();
          } else {
            builder.breakOp(" ");
          }
          scan(node.getBody(), null);
          builder.close();
        }
        builder.guessToken(";");
      }
    }
    return null;
  }

  @Override
  public Void visitSwitch(SwitchTree node, Void unused) {
    sync(node);
    visitSwitch(node.getExpression(), node.getCases());
    return null;
  }

  private void visitSwitch(ExpressionTree expression, List<? extends CaseTree> cases) {
    token("switch");
    builder.space();
    token("(");
    scan(skipParen(expression), null);
    token(")");
    builder.space();
    tokenBreakTrailingComment("{", plusTwo);
    builder.blankLineWanted(BlankLineWanted.NO);
    builder.open(plusTwo);
    boolean afterFirstToken = false;
    for (CaseTree caseTree : cases) {
      if (afterFirstToken) {
        builder.blankLineWanted(BlankLineWanted.PRESERVE);
      }
      scan(caseTree, null);
      afterFirstToken = true;
    }
    builder.close();
    builder.forcedBreak();
    builder.blankLineWanted(BlankLineWanted.NO);
    token("}", plusFour);
  }

  @Override
  public Void visitSynchronized(SynchronizedTree node, Void unused) {
    sync(node);
    token("synchronized");
    builder.space();
    token("(");
    builder.open(plusFour);
    builder.breakOp();
    scan(skipParen(node.getExpression()), null);
    builder.close();
    token(")");
    builder.space();
    scan(node.getBlock(), null);
    return null;
  }

  @Override
  public Void visitThrow(ThrowTree node, Void unused) {
    sync(node);
    token("throw");
    builder.space();
    scan(node.getExpression(), null);
    token(";");
    return null;
  }

  @Override
  public Void visitTry(TryTree node, Void unused) {
    sync(node);
    builder.open(ZERO);
    token("try");
    builder.space();
    if (!node.getResources().isEmpty()) {
      token("(");
      boolean multipleResources = node.getResources().size() > 1;
      builder.open(
          multipleResources
              ? Indent.Align.toCurrentColumn(maximumAlignment, plusFour)
              : ZERO);
      if (multipleResources) {
        builder.breakOp(INDEPENDENT, "", ZERO);
      }
      boolean afterFirstToken = false;
      for (Tree resource : node.getResources()) {
        if (afterFirstToken) {
          builder.forcedBreak();
        }
        if (resource instanceof VariableTree variableTree) {

          declareOne(
              DeclarationKind.PARAMETER,
              variableAnnotationDirection(variableTree.getModifiers()),
              Optional.of(variableTree.getModifiers()),
              variableTree.getType(),
              /* name= */ variableTree.getName(),
              "",
              "=",
              Optional.ofNullable(variableTree.getInitializer()),
              /* trailing= */ Optional.empty(),
              /* receiverExpression= */ Optional.empty(),
              /* typeWithDims= */ Optional.empty());
        } else {
          // TODO(cushon): think harder about what to do with `try (resource1; resource2) {}`
          scan(resource, null);
        }
        if (builder.peekToken().equals(Optional.of(";"))) {
          token(";");
          builder.space();
        }
        afterFirstToken = true;
      }
      if (builder.peekToken().equals(Optional.of(";"))) {
        token(";");
        builder.space();
      }
      token(")");
      builder.close();
      builder.space();
    }
    // An empty try-with-resources body can collapse to "{}" if there are no trailing catch or
    // finally blocks.
    boolean trailingClauses = !node.getCatches().isEmpty() || node.getFinallyBlock() != null;
    visitBlock(
        node.getBlock(),
        CollapseEmptyOrNot.valueOf(!trailingClauses),
        AllowLeadingBlankLine.YES,
        AllowTrailingBlankLine.valueOf(trailingClauses));
    for (int i = 0; i < node.getCatches().size(); i++) {
      CatchTree catchClause = node.getCatches().get(i);
      trailingClauses = i < node.getCatches().size() - 1 || node.getFinallyBlock() != null;
      visitCatchClause(catchClause, AllowTrailingBlankLine.valueOf(trailingClauses));
    }
    if (node.getFinallyBlock() != null) {
      builder.space();
      token("finally");
      builder.space();
      visitBlock(
          node.getFinallyBlock(),
          CollapseEmptyOrNot.NO,
          AllowLeadingBlankLine.YES,
          AllowTrailingBlankLine.NO);
    }
    builder.close();
    return null;
  }

  private void visitClassDeclaration(ClassTree node) {
    sync(node);
    typeDeclarationModifiers(node.getModifiers());
    List<? extends Tree> permitsTypes = node.getPermitsClause();
    boolean hasSuperclassType = node.getExtendsClause() != null;
    boolean hasSuperInterfaceTypes = !node.getImplementsClause().isEmpty();
    boolean hasPermitsTypes = !permitsTypes.isEmpty();
    int clauseComplexity = 0;
    if (hasSuperclassType) {
      clauseComplexity = 1 + memberComplexity(node.getExtendsClause());
    }
    if (hasSuperInterfaceTypes) {
      clauseComplexity++;
      for (Tree type : node.getImplementsClause()) {
        clauseComplexity += memberComplexity(type);
      }
    }
    if (hasPermitsTypes) {
      clauseComplexity++;
      for (Tree type : permitsTypes) {
        clauseComplexity += memberComplexity(type);
      }
    }
    boolean breakComposedClauses = clauseComplexity > MAX_UNBROKEN_COMPLEXITY;
    token(node.getKind() == Tree.Kind.INTERFACE ? "interface" : "class");
    builder.space();
    visit(node.getSimpleName());
    if (!node.getTypeParameters().isEmpty()) {
      token("<");
    }
    builder.open(plusFour);
    {
      if (!node.getTypeParameters().isEmpty()) {
        typeParametersRest(
            node.getTypeParameters(),
            hasSuperclassType || hasSuperInterfaceTypes || hasPermitsTypes ? plusFour : ZERO);
      }
      boolean afterClause = false;
      if (hasSuperclassType) {
        builder.breakToFill(" ");
        token("extends");
        builder.space();
        scan(node.getExtendsClause(), null);
        afterClause = true;
      }
      classDeclarationTypeList(
          node.getKind() == Tree.Kind.INTERFACE ? "extends" : "implements",
          node.getImplementsClause(),
          breakComposedClauses && afterClause);
      afterClause |= hasSuperInterfaceTypes;
      classDeclarationTypeList(
          "permits", permitsTypes, breakComposedClauses && afterClause);
    }
    builder.close();
    if (node.getMembers() == null) {
      token(";");
    } else {
      addBodyDeclarations(node.getMembers(), BracesOrNot.YES, FirstDeclarationsOrNot.YES);
    }
    dropEmptyDeclarations();
  }

  @Override
  public Void visitTypeParameter(TypeParameterTree node, Void unused) {
    sync(node);
    builder.open(ZERO);
    visitAnnotations(node.getAnnotations(), BreakOrNot.NO, BreakOrNot.YES);
    visit(node.getName());
    if (!node.getBounds().isEmpty()) {
      builder.space();
      token("extends");
      builder.open(plusFour);
      builder.breakOp(" ");
      boolean afterFirstToken = false;
      int remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
      for (Tree typeBound : node.getBounds()) {
        int complexity = memberComplexity(typeBound);
        if (afterFirstToken) {
          if (complexity > remainingComplexity) {
            builder.forcedBreak();
            remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
          } else {
            builder.breakToFill(" ");
          }
          token("&");
          builder.space();
        }
        scan(typeBound, null);
        remainingComplexity -= complexity;
        afterFirstToken = true;
      }
      builder.close();
    }
    builder.close();
    return null;
  }

  @Override
  public Void visitUnionType(UnionTypeTree node, Void unused) {
    throw new IllegalStateException("expected manual descent into union types");
  }

  @Override
  public Void visitWhileLoop(WhileLoopTree node, Void unused) {
    sync(node);
    token("while");
    builder.space();
    token("(");
    scan(skipParen(node.getCondition()), null);
    token(")");
    visitStatement(
        node.getStatement(),
        CollapseEmptyOrNot.YES,
        AllowLeadingBlankLine.YES,
        AllowTrailingBlankLine.NO);
    return null;
  }

  @Override
  public Void visitWildcard(WildcardTree node, Void unused) {
    sync(node);
    builder.open(ZERO);
    token("?");
    if (node.getBound() != null) {
      builder.open(plusFour);
      builder.space();
      token(node.getKind() == EXTENDS_WILDCARD ? "extends" : "super");
      builder.breakOp(" ");
      scan(node.getBound(), null);
      builder.close();
    }
    builder.close();
    return null;
  }

  // Helper methods.

  /** Helper method for annotations. */
  private void visitAnnotations(
      List<? extends AnnotationTree> annotations, BreakOrNot breakBefore, BreakOrNot breakAfter) {
    if (!annotations.isEmpty()) {
      if (breakBefore.isYes()) {
        builder.breakToFill(" ");
      }
      boolean afterFirstToken = false;
      for (AnnotationTree annotation : annotations) {
        if (afterFirstToken) {
          builder.breakToFill(" ");
        }
        scan(annotation, null);
        afterFirstToken = true;
      }
      if (breakAfter.isYes()) {
        builder.breakToFill(" ");
      }
    }
  }

  private void verticalAnnotations(List<AnnotationTree> annotations) {
    for (AnnotationTree annotation : annotations) {
      builder.forcedBreak();
      scan(annotation, null);
      builder.forcedBreak();
    }
  }

  /** Helper method for blocks. */
  private void visitBlock(
      BlockTree node,
      CollapseEmptyOrNot collapseEmptyOrNot,
      AllowLeadingBlankLine allowLeadingBlankLine,
      AllowTrailingBlankLine allowTrailingBlankLine) {
    sync(node);
    if (node.isStatic()) {
      token("static");
      builder.space();
    }
    if (collapseEmptyOrNot.isYes() && node.getStatements().isEmpty()) {
      if (builder.peekToken().equals(Optional.of(";"))) {
        // TODO(cushon): is this needed?
        token(";");
      } else {
        tokenBreakTrailingComment("{", plusTwo);
        builder.blankLineWanted(BlankLineWanted.NO);
        token("}", plusTwo);
      }
    } else {
      builder.open(ZERO);
      builder.open(plusTwo);
      tokenBreakTrailingComment("{", plusTwo);
      if (allowLeadingBlankLine == AllowLeadingBlankLine.NO) {
        builder.blankLineWanted(BlankLineWanted.NO);
      } else {
        builder.blankLineWanted(BlankLineWanted.PRESERVE);
      }
      visitStatements(node.getStatements());
      builder.close();
      builder.forcedBreak();
      builder.close();
      if (allowTrailingBlankLine == AllowTrailingBlankLine.NO) {
        builder.blankLineWanted(BlankLineWanted.NO);
      } else {
        builder.blankLineWanted(BlankLineWanted.PRESERVE);
      }
      markForPartialFormat();
      token("}", plusTwo);
    }
  }

  /** Helper method for statements. */
  private void visitStatement(
      StatementTree node,
      CollapseEmptyOrNot collapseEmptyOrNot,
      AllowLeadingBlankLine allowLeadingBlank,
      AllowTrailingBlankLine allowTrailingBlank) {
    sync(node);
    switch (node.getKind()) {
      case BLOCK -> {
        builder.space();
        visitBlock((BlockTree) node, collapseEmptyOrNot, allowLeadingBlank, allowTrailingBlank);
      }
      default -> {
        builder.open(plusTwo);
        builder.forcedBreak();
        scan(node, null);
        builder.close();
      }
    }
  }

  private void visitStatements(List<? extends StatementTree> statements) {
    boolean afterFirstToken = false;
    PeekingIterator<StatementTree> it = Iterators.peekingIterator(statements.iterator());
    dropEmptyDeclarations();
    while (it.hasNext()) {
      StatementTree tree = it.next();
      builder.forcedBreak();
      if (afterFirstToken) {
        builder.blankLineWanted(BlankLineWanted.PRESERVE);
      }
      markForPartialFormat();
      afterFirstToken = true;
      List<VariableTree> fragments = variableFragments(it, tree);
      if (!fragments.isEmpty()) {
        visitVariables(
            fragments,
            DeclarationKind.NONE,
            canLocalHaveHorizontalAnnotations(fragments.get(0).getModifiers()));
      } else {
        scan(tree, null);
      }
    }
  }

  private void typeDeclarationModifiers(ModifiersTree modifiers) {
    List<AnnotationTree> typeAnnotations =
        visitModifiers(
            modifiers, Direction.VERTICAL, /* declarationAnnotationBreak= */ Optional.empty());
    verticalAnnotations(typeAnnotations);
  }

  /** Output combined modifiers and annotations and the trailing break. */
  private void visitAndBreakModifiers(
      ModifiersTree modifiers,
      Direction annotationDirection,
      Optional<BreakTag> declarationAnnotationBreak) {
    List<AnnotationTree> typeAnnotations =
        visitModifiers(modifiers, annotationDirection, declarationAnnotationBreak);
    visitAnnotations(typeAnnotations, BreakOrNot.NO, BreakOrNot.YES);
  }

  @Override
  public Void visitModifiers(ModifiersTree node, Void unused) {
    throw new IllegalStateException("expected manual descent into modifiers");
  }

  /** Output combined modifiers and annotations and returns the trailing break. */
  @CheckReturnValue
  private ImmutableList<AnnotationTree> visitModifiers(
      ModifiersTree modifiersTree,
      Direction annotationsDirection,
      Optional<BreakTag> declarationAnnotationBreak) {
    return visitModifiers(
        modifiersTree,
        modifiersTree.getAnnotations(),
        annotationsDirection,
        declarationAnnotationBreak);
  }

  @CheckReturnValue
  private ImmutableList<AnnotationTree> visitModifiers(
      ModifiersTree modifiersTree,
      List<? extends AnnotationTree> annotationTrees,
      Direction annotationsDirection,
      Optional<BreakTag> declarationAnnotationBreak) {
    DeclarationModifiersAndTypeAnnotations splitModifiers =
        splitModifiers(modifiersTree, annotationTrees);
    return visitModifiers(splitModifiers, annotationsDirection, declarationAnnotationBreak);
  }

  @CheckReturnValue
  private ImmutableList<AnnotationTree> visitModifiers(
      DeclarationModifiersAndTypeAnnotations splitModifiers,
      Direction annotationsDirection,
      Optional<BreakTag> declarationAnnotationBreak) {
    if (splitModifiers.declarationModifiers().isEmpty()) {
      return splitModifiers.typeAnnotations();
    }
    Deque<AnnotationOrModifier> declarationModifiers =
        new ArrayDeque<>(splitModifiers.declarationModifiers());
    builder.open(ZERO);
    boolean afterFirstToken = false;
    boolean lastWasAnnotation = false;
    while (!declarationModifiers.isEmpty() && !declarationModifiers.peekFirst().isModifier()) {
      if (afterFirstToken) {
        builder.addAll(
            annotationsDirection.isVertical()
                ? forceBreakList(declarationAnnotationBreak)
                : breakList(declarationAnnotationBreak));
      }
      formatAnnotationOrModifier(declarationModifiers);
      afterFirstToken = true;
      lastWasAnnotation = true;
    }
    builder.close();
    ImmutableList<Op> trailingBreak =
        annotationsDirection.isVertical()
            ? forceBreakList(declarationAnnotationBreak)
            : breakList(declarationAnnotationBreak);
    if (declarationModifiers.isEmpty()) {
      builder.addAll(trailingBreak);
      return splitModifiers.typeAnnotations();
    }
    if (lastWasAnnotation) {
      builder.addAll(trailingBreak);
    }

    builder.open(ZERO);
    afterFirstToken = false;
    while (!declarationModifiers.isEmpty()) {
      if (afterFirstToken) {
        builder.addAll(breakFillList(Optional.empty()));
      }
      formatAnnotationOrModifier(declarationModifiers);
      afterFirstToken = true;
    }
    builder.close();
    builder.addAll(breakFillList(Optional.empty()));
    return splitModifiers.typeAnnotations();
  }

  /** Represents an annotation or a modifier in a {@link ModifiersTree}. */
  @AutoOneOf(AnnotationOrModifier.Kind.class)
  abstract static class AnnotationOrModifier implements Comparable<AnnotationOrModifier> {
    enum Kind {
      MODIFIER,
      ANNOTATION
    }

    abstract Kind getKind();

    abstract AnnotationTree annotation();

    abstract Input.Tok modifier();

    static AnnotationOrModifier ofModifier(Input.Tok m) {
      return AutoOneOf_JavaInputAstVisitor_AnnotationOrModifier.modifier(m);
    }

    static AnnotationOrModifier ofAnnotation(AnnotationTree a) {
      return AutoOneOf_JavaInputAstVisitor_AnnotationOrModifier.annotation(a);
    }

    boolean isModifier() {
      return getKind().equals(Kind.MODIFIER);
    }

    boolean isAnnotation() {
      return getKind().equals(Kind.ANNOTATION);
    }

    int position() {
      return switch (getKind()) {
        case MODIFIER -> modifier().getPosition();
        case ANNOTATION -> getStartPosition(annotation());
      };
    }

    private static final Comparator<AnnotationOrModifier> COMPARATOR =
        Comparator.comparingInt(AnnotationOrModifier::position);

    @Override
    public int compareTo(AnnotationOrModifier o) {
      return COMPARATOR.compare(this, o);
    }
  }

  /**
   * The modifiers annotations for a declaration, grouped in to a prefix that contains all of the
   * declaration annotations and modifiers, and a suffix of type annotations.
   *
   * <p>For examples like {@code @Deprecated public @Nullable Foo foo();}, this allows us to format
   * {@code @Deprecated public} as declaration modifiers, and {@code @Nullable} as a type annotation
   * on the return type.
   */
  private record DeclarationModifiersAndTypeAnnotations(
      ImmutableList<AnnotationOrModifier> declarationModifiers,
      ImmutableList<AnnotationTree> typeAnnotations) {
    DeclarationModifiersAndTypeAnnotations {
      requireNonNull(declarationModifiers, "declarationModifiers");
      requireNonNull(typeAnnotations, "typeAnnotations");
    }

    static DeclarationModifiersAndTypeAnnotations create(
        ImmutableList<AnnotationOrModifier> declarationModifiers,
        ImmutableList<AnnotationTree> typeAnnotations) {
      return new DeclarationModifiersAndTypeAnnotations(declarationModifiers, typeAnnotations);
    }

    static DeclarationModifiersAndTypeAnnotations empty() {
      return create(ImmutableList.of(), ImmutableList.of());
    }

    boolean hasDeclarationAnnotation() {
      return declarationModifiers().stream().anyMatch(AnnotationOrModifier::isAnnotation);
    }
  }

  /**
   * Examines the token stream to convert the modifiers for a declaration into a {@link
   * DeclarationModifiersAndTypeAnnotations}.
   */
  private DeclarationModifiersAndTypeAnnotations splitModifiers(
      ModifiersTree modifiersTree, List<? extends AnnotationTree> annotations) {
    if (annotations.isEmpty() && !isModifier(builder.peekToken().get())) {
      return DeclarationModifiersAndTypeAnnotations.empty();
    }
    RangeSet<Integer> annotationRanges = TreeRangeSet.create();
    for (AnnotationTree annotationTree : annotations) {
      annotationRanges.add(
          Range.closedOpen(
              getStartPosition(annotationTree), getEndPosition(annotationTree, getCurrentPath())));
    }
    ImmutableList<Input.Tok> toks =
        builder.peekTokens(
            getStartPosition(modifiersTree),
            (Input.Tok tok) ->
                // ModifiersTree end position information isn't reliable, so scan tokens as long as
                // we're seeing annotations or modifiers
                annotationRanges.contains(tok.getPosition()) || isModifier(tok.getText()));
    ImmutableList<AnnotationOrModifier> modifiers =
        ImmutableList.copyOf(
            Streams.concat(
                    toks.stream()
                        // reject tokens from inside AnnotationTrees, we only want modifiers
                        .filter(t -> !annotationRanges.contains(t.getPosition()))
                        .map(AnnotationOrModifier::ofModifier),
                    annotations.stream().map(AnnotationOrModifier::ofAnnotation))
                .sorted()
                .collect(toList()));
    // Take a suffix of annotations that are well-known type annotations, and which appear after any
    // declaration annotations or modifiers
    ImmutableList.Builder<AnnotationTree> typeAnnotations = ImmutableList.builder();
    int idx = modifiers.size() - 1;
    while (idx >= 0) {
      AnnotationOrModifier modifier = modifiers.get(idx);
      if (!modifier.isAnnotation() || !isTypeAnnotation(modifier.annotation())) {
        break;
      }
      typeAnnotations.add(modifier.annotation());
      idx--;
    }
    return DeclarationModifiersAndTypeAnnotations.create(
        modifiers.subList(0, idx + 1), typeAnnotations.build().reverse());
  }

  private void formatAnnotationOrModifier(Deque<AnnotationOrModifier> modifiers) {
    AnnotationOrModifier modifier = modifiers.removeFirst();
    switch (modifier.getKind()) {
      case MODIFIER -> {
        token(modifier.modifier().getText());
        if (modifier.modifier().getText().equals("non")) {
          token(modifiers.removeFirst().modifier().getText());
          token(modifiers.removeFirst().modifier().getText());
        }
      }
      case ANNOTATION -> scan(modifier.annotation(), null);
    }
  }

  private boolean isTypeAnnotation(AnnotationTree annotationTree) {
    Tree annotationType = annotationTree.getAnnotationType();
    return switch (annotationType) {
      case IdentifierTree identifierTree ->
          typeAnnotationSimpleNames.contains(identifierTree.getName());
      default -> false;
    };
  }

  private static boolean isModifier(String token) {
    return switch (token) {
      case "public",
          "protected",
          "private",
          "abstract",
          "static",
          "final",
          "transient",
          "volatile",
          "synchronized",
          "native",
          "strictfp",
          "default",
          "sealed",
          "non",
          "-" ->
          true;
      default -> false;
    };
  }

  @Override
  public Void visitCatch(CatchTree node, Void unused) {
    throw new IllegalStateException("expected manual descent into catch trees");
  }

  /** Helper method for {@link CatchTree}s. */
  private void visitCatchClause(CatchTree node, AllowTrailingBlankLine allowTrailingBlankLine) {
    sync(node);
    builder.space();
    token("catch");
    builder.space();
    token("(");
    builder.open(plusFour);
    VariableTree ex = node.getParameter();
    if (ex.getType().getKind() == UNION_TYPE) {
      builder.open(ZERO);
      visitUnionType(ex);
      builder.close();
    } else {
      // TODO(cushon): don't break after here for consistency with for, while, etc.
      builder.breakToFill();
      builder.open(ZERO);
      scan(ex, null);
      builder.close();
    }
    builder.close();
    token(")");
    builder.space();
    visitBlock(
        node.getBlock(), CollapseEmptyOrNot.NO, AllowLeadingBlankLine.YES, allowTrailingBlankLine);
  }

  /** Formats a union type declaration in a catch clause. */
  private void visitUnionType(VariableTree declaration) {
    UnionTypeTree type = (UnionTypeTree) declaration.getType();
    builder.open(ZERO);
    sync(declaration);
    visitAndBreakModifiers(
        declaration.getModifiers(),
        Direction.HORIZONTAL,
        /* declarationAnnotationBreak= */ Optional.empty());
    List<? extends Tree> union = type.getTypeAlternatives();
    int remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
    for (int i = 0; i < union.size(); i++) {
      Tree alternative = union.get(i);
      int complexity = memberComplexity(alternative);
      if (i > 0) {
        if (complexity > remainingComplexity) {
          builder.breakOp(Doc.FillMode.FORCED, " ", plusFour);
          remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
        } else {
          builder.breakOp(" ");
        }
        token("|");
        builder.space();
      }
      if (i < union.size() - 1) {
        scan(alternative, null);
      } else {
        declareOne(
            DeclarationKind.NONE,
            Direction.HORIZONTAL,
            /* modifiers= */ Optional.empty(),
            alternative,
            /* name= */ declaration.getName(),
            /* op= */ "",
            "=",
            Optional.ofNullable(declaration.getInitializer()),
            /* trailing= */ Optional.empty(),
            /* receiverExpression= */ Optional.empty(),
            /* typeWithDims= */ Optional.empty());
      }
      remainingComplexity -= complexity;
    }
    builder.close();
  }

  /** Accumulate the operands and operators. */
  private static void walkInfix(
      int precedence,
      ExpressionTree expression,
      List<ExpressionTree> operands,
      List<String> operators) {
    if (expression instanceof BinaryTree binaryTree) {

      if (precedence(binaryTree) == precedence) {
        walkInfix(precedence, binaryTree.getLeftOperand(), operands, operators);
        operators.add(operatorName(expression));
        walkInfix(precedence, binaryTree.getRightOperand(), operands, operators);
      } else {
        operands.add(expression);
      }
    } else {
      operands.add(expression);
    }
  }

  private void visitFormals(
      Optional<VariableTree> receiver, List<? extends VariableTree> parameters) {
    if (!receiver.isPresent() && parameters.isEmpty()) {
      return;
    }
    builder.open(ZERO);
    visitFormalsContents(
        receiver,
        parameters,
        UNIFIED,
        /* breakStructurally= */ false,
        /* precedingStructure= */ 0,
        /* parameterStart= */ null);
    builder.close();
  }

  private void visitAlignedFormals(
      Optional<VariableTree> receiver, List<? extends VariableTree> parameters) {
    visitAlignedFormals(receiver, parameters, /* precedingStructure= */ 0);
  }

  private void visitAlignedFormals(
      Optional<VariableTree> receiver,
      List<? extends VariableTree> parameters,
      int precedingStructure) {
    if (parameterListLayout(receiver, parameters, precedingStructure) == ListLayout.BROKEN) {
      visitBrokenFormals(receiver, parameters);
      return;
    }
    // Sun's +8 fallback avoids moving method parameters more than three continuations right.
    BreakTag parameterStart = genSym();
    builder.open(Indent.Align.toCurrentColumn(maximumAlignment));
    builder.breakOp(INDEPENDENT, "", ZERO, Optional.of(parameterStart));
    visitFormalsContents(
        receiver,
        parameters,
        INDEPENDENT,
        /* breakStructurally= */ true,
        precedingStructure,
        parameterStart);
    builder.close();
  }

  private void visitBrokenFormals(
      Optional<VariableTree> receiver, List<? extends VariableTree> parameters) {
    builder.open(plusFour);
    builder.forcedBreak();
    if (receiver.isPresent()) {
      VariableTree receiverParameter = receiver.get();
      declareOne(
          DeclarationKind.PARAMETER,
          Direction.HORIZONTAL,
          Optional.of(receiverParameter.getModifiers()),
          receiverParameter.getType(),
          /* name= */ receiverParameter.getName(),
          "",
          "",
          /* initializer= */ Optional.empty(),
          !parameters.isEmpty() ? Optional.of(",") : Optional.empty(),
          Optional.of(receiverParameter.getNameExpression()),
          /* typeWithDims= */ Optional.empty());
      if (!parameters.isEmpty()) {
        builder.forcedBreak();
      }
    }
    for (int i = 0; i < parameters.size(); i++) {
      if (i > 0) {
        builder.forcedBreak();
      }
      visitToDeclare(
          DeclarationKind.PARAMETER,
          Direction.HORIZONTAL,
          parameters.get(i),
          /* initializer= */ Optional.empty(),
          "=",
          i < parameters.size() - 1 ? Optional.of(",") : Optional.empty());
    }
    builder.close();
  }

  private void visitFormalsContents(
      Optional<VariableTree> receiver,
      List<? extends VariableTree> parameters,
      FillMode fillMode,
      boolean breakStructurally,
      int precedingStructure,
      BreakTag parameterStart) {
    boolean afterFirstToken = false;
    List<BreakTag> previousBreaks = new ArrayList<>();
    if (parameterStart != null) {
      previousBreaks.add(parameterStart);
    }
    int remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
    int remainingStructure = MAX_UNBROKEN_COMPLEXITY - precedingStructure;
    if (receiver.isPresent()) {
      declareOne(
          DeclarationKind.PARAMETER,
          Direction.HORIZONTAL,
          Optional.of(receiver.get().getModifiers()),
          receiver.get().getType(),
          /* name= */ receiver.get().getName(),
          "",
          "",
          /* initializer= */ Optional.empty(),
          !parameters.isEmpty() ? Optional.of(",") : Optional.empty(),
          Optional.of(receiver.get().getNameExpression()),
          /* typeWithDims= */ Optional.empty());
      remainingComplexity -= memberComplexity(receiver.get());
      remainingStructure -= parameterStructure(receiver.get());
      afterFirstToken = true;
    }
    for (int i = 0; i < parameters.size(); i++) {
      VariableTree parameter = parameters.get(i);
      int complexity = memberComplexity(parameter);
      int structure = parameterStructure(parameter);
      if (afterFirstToken) {
        BreakTag currentBreak = genSym();
        if (breakStructurally
            && (complexity > remainingComplexity || structure > remainingStructure)) {
          Indent indent =
              preservePreviousIndent(
                  previousBreaks,
                  Indent.Align.toBreakColumn(parameterStart, maximumAlignment, plusFour));
          builder.breakOp(FillMode.FORCED, "", indent, Optional.of(currentBreak));
          remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
          remainingStructure = MAX_UNBROKEN_COMPLEXITY;
        } else {
          Indent indent = preservePreviousIndent(previousBreaks, ZERO);
          builder.breakOp(
              fillMode,
              " ",
              indent,
              parameterStart == null ? Optional.empty() : Optional.of(currentBreak));
        }
        if (parameterStart != null) {
          previousBreaks.add(currentBreak);
        }
      }
      remainingComplexity -= complexity;
      remainingStructure -= structure;
      visitToDeclare(
          DeclarationKind.PARAMETER,
          Direction.HORIZONTAL,
          parameter,
          /* initializer= */ Optional.empty(),
          "=",
          i < parameters.size() - 1 ? Optional.of(",") : /* a= */ Optional.empty());
      afterFirstToken = true;
    }
  }

  //  /** Helper method for {@link MethodDeclaration}s. */
  private void visitThrowsClause(List<? extends ExpressionTree> thrownExceptionTypes) {
    token("throws");
    builder.space();
    BreakTag exceptionStart = genSym();
    builder.open(Indent.Align.toCurrentColumn(maximumAlignment, plusFour));
    builder.breakOp(Doc.FillMode.INDEPENDENT, "", ZERO, Optional.of(exceptionStart));
    boolean afterFirstToken = false;
    int remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
    List<BreakTag> previousBreaks = new ArrayList<>(List.of(exceptionStart));
    for (ExpressionTree thrownExceptionType : thrownExceptionTypes) {
      int complexity = memberComplexity(thrownExceptionType);
      if (afterFirstToken) {
        token(",");
        BreakTag currentBreak = genSym();
        if (complexity > remainingComplexity) {
          Indent indent =
              preservePreviousIndent(
                  previousBreaks,
                  Indent.Align.toBreakColumn(exceptionStart, maximumAlignment, plusFour));
          builder.breakOp(FillMode.FORCED, " ", indent, Optional.of(currentBreak));
          remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
        } else {
          Indent indent = preservePreviousIndent(previousBreaks, ZERO);
          builder.breakOp(INDEPENDENT, " ", indent, Optional.of(currentBreak));
        }
        previousBreaks.add(currentBreak);
      }
      scan(thrownExceptionType, null);
      remainingComplexity -= complexity;
      afterFirstToken = true;
    }
    builder.close();
  }

  @Override
  public Void visitIdentifier(IdentifierTree node, Void unused) {
    sync(node);
    token(node.getName().toString());
    return null;
  }

  @Override
  public Void visitModule(ModuleTree node, Void unused) {
    recordMarkdownJavadocPosition(node);
    for (AnnotationTree annotation : node.getAnnotations()) {
      scan(annotation, null);
      builder.forcedBreak();
    }
    if (node.getModuleType() == ModuleTree.ModuleKind.OPEN) {
      token("open");
      builder.space();
    }
    token("module");
    builder.space();
    scan(node.getName(), null);
    builder.space();
    if (node.getDirectives().isEmpty()) {
      tokenBreakTrailingComment("{", plusTwo);
      builder.blankLineWanted(BlankLineWanted.NO);
      token("}", plusTwo);
    } else {
      builder.open(plusTwo);
      token("{");
      builder.forcedBreak();
      Optional<Tree.Kind> previousDirective = Optional.empty();
      for (DirectiveTree directiveTree : node.getDirectives()) {
        markForPartialFormat();
        builder.blankLineWanted(
            previousDirective.map(k -> !k.equals(directiveTree.getKind())).orElse(false)
                ? BlankLineWanted.YES
                : BlankLineWanted.NO);
        builder.forcedBreak();
        scan(directiveTree, null);
        previousDirective = Optional.of(directiveTree.getKind());
      }
      builder.close();
      builder.forcedBreak();
      token("}");
    }
    return null;
  }

  private void visitDirective(
      String name,
      String separator,
      ExpressionTree nameExpression,
      @Nullable List<? extends ExpressionTree> items) {
    token(name);
    builder.space();
    scan(nameExpression, null);
    if (items != null) {
      builder.open(plusFour);
      builder.space();
      token(separator);
      builder.forcedBreak();
      boolean afterFirstToken = false;
      for (ExpressionTree item : items) {
        if (afterFirstToken) {
          token(",");
          builder.forcedBreak();
        }
        scan(item, null);
        afterFirstToken = true;
      }
      token(";");
      builder.close();
    } else {
      token(";");
    }
  }

  @Override
  public Void visitExports(ExportsTree node, Void unused) {
    visitDirective("exports", "to", node.getPackageName(), node.getModuleNames());
    return null;
  }

  @Override
  public Void visitOpens(OpensTree node, Void unused) {
    visitDirective("opens", "to", node.getPackageName(), node.getModuleNames());
    return null;
  }

  @Override
  public Void visitProvides(ProvidesTree node, Void unused) {
    visitDirective("provides", "with", node.getServiceName(), node.getImplementationNames());
    return null;
  }

  @Override
  public Void visitRequires(RequiresTree node, Void unused) {
    token("requires");
    builder.space();
    while (true) {
      if (builder.peekToken().equals(Optional.of("static"))) {
        token("static");
        builder.space();
      } else if (builder.peekToken().equals(Optional.of("transitive"))) {
        token("transitive");
        builder.space();
      } else {
        break;
      }
    }
    scan(node.getModuleName(), null);
    token(";");
    return null;
  }

  @Override
  public Void visitUses(UsesTree node, Void unused) {
    token("uses");
    builder.space();
    scan(node.getServiceName(), null);
    token(";");
    return null;
  }

  /** Helper method for import declarations, names, and qualified names. */
  private void visitName(Tree node) {
    Deque<Name> stack = new ArrayDeque<>();
    while (node instanceof MemberSelectTree memberSelectTree) {
      stack.addFirst(memberSelectTree.getIdentifier());
      node = memberSelectTree.getExpression();
    }
    stack.addFirst(((IdentifierTree) node).getName());
    boolean afterFirstToken = false;
    for (Name name : stack) {
      if (afterFirstToken) {
        token(".");
      }
      token(name.toString());
      afterFirstToken = true;
    }
  }

  private void visitToDeclare(
      DeclarationKind kind,
      Direction annotationsDirection,
      VariableTree node,
      Optional<ExpressionTree> initializer,
      String equals,
      Optional<String> trailing) {
    sync(node);
    Optional<TypeWithDims> typeWithDims;
    Tree type;
    if (node.getType() != null) {
      TypeWithDims extractedDims = DimensionHelpers.extractDims(node.getType(), SortedDims.YES);
      typeWithDims = Optional.of(extractedDims);
      type = extractedDims.node();
    } else {
      typeWithDims = Optional.empty();
      type = null;
    }
    declareOne(
        kind,
        annotationsDirection,
        Optional.of(node.getModifiers()),
        type,
        node.getName(),
        "",
        equals,
        initializer,
        trailing,
        /* receiverExpression= */ Optional.empty(),
        typeWithDims);
  }

  /** Does not omit the leading {@code "<"}, which should be associated with the type name. */
  private void typeParametersRest(
      List<? extends TypeParameterTree> typeParameters, Indent plusIndent) {
    builder.open(plusIndent);
    builder.breakOp();
    builder.open(ZERO);
    boolean afterFirstToken = false;
    for (TypeParameterTree typeParameter : typeParameters) {
      if (afterFirstToken) {
        token(",");
        builder.breakOp(" ");
      }
      scan(typeParameter, null);
      afterFirstToken = true;
    }
    token(">");
    builder.close();
    builder.close();
  }

  /** Formats a dot chain across method calls, field accesses, and array accesses. */
  private void visitDot(ExpressionTree node0) {
    ExpressionTree node = node0;

    // collect a flattened list of "."-separated items
    // e.g. ImmutableList.builder().add(1).build() -> [ImmutableList, builder(), add(1), build()]
    Deque<ExpressionTree> stack = new ArrayDeque<>();
    LOOP:
    do {
      stack.addFirst(node);
      if (node.getKind() == ARRAY_ACCESS) {
        node = getArrayBase(node);
      }
      switch (node.getKind()) {
        case MEMBER_SELECT, METHOD_INVOCATION -> node = chainReceiver(node);
        case IDENTIFIER -> {
          node = null;
          break LOOP;
        }
        default -> {
          // If the dot chain starts with a primary expression
          // (e.g. a class instance creation, or a conditional expression)
          // then remove it from the list and deal with it first.
          node = stack.removeFirst();
          break LOOP;
        }
      }
    } while (node != null);
    List<ExpressionTree> items = new ArrayList<>(stack);

    int firstInvocationIndex = -1;
    for (int i = 0; i < items.size(); i++) {
      if (getArrayBase(items.get(i)).getKind() == METHOD_INVOCATION) {
        firstInvocationIndex = i;
        break;
      }
    }
    int callCount = chainCallCount(node0);
    boolean forceSelectorBreaks = 1 + 2 * callCount > MAX_UNBROKEN_COMPLEXITY;
    if (callCount > 1 && layoutContext == LayoutContext.UNBROKEN) {
      forceSelectorBreaks |= nestedChainWidthScore(node0) > ROW_BREAK_SCORE;
    }
    if (node != null && !items.isEmpty()) {
      long selectorWidthScore =
          (long) memberLayoutWeight(items.get(0)) * (syntaxWidth(getArrayBase(node)) + 2);
      forceSelectorBreaks |= selectorWidthScore > ROW_BREAK_SCORE;
    }

    boolean needDot = false;

    // Emit a constructor, parenthesized expression, or other primary before its selector suffix.
    if (node != null) {
      ExpressionTree primary = getArrayBase(node);
      while (primary instanceof ParenthesizedTree parenthesized) {
        primary = parenthesized.getExpression();
      }
      boolean breakAfterPrimary =
          primary instanceof ConditionalExpressionTree conditional
              && conditionalComplexity(conditional) > MAX_UNBROKEN_COMPLEXITY;
      // Exception: if it's an anonymous class declaration, we don't need to
      // break and indent after the trailing '}'.
      if (node.getKind() == NEW_CLASS && ((NewClassTree) node).getClassBody() != null) {
        builder.open(ZERO);
        scan(getArrayBase(node), null);
        token(".");
      } else {
        LayoutContext previousContext = layoutContext;
        if (forceSelectorBreaks) {
          layoutContext = LayoutContext.BROKEN_CHAIN;
        }
        try {
          scan(getArrayBase(node), null);
        } finally {
          layoutContext = previousContext;
        }
        builder.open(plusFour);
        FillMode fillMode = node.getKind() == NEW_CLASS ? UNIFIED : INDEPENDENT;
        if (breakAfterPrimary || (forceSelectorBreaks && chainCallCount(node) > 0)) {
          fillMode = FillMode.FORCED;
        }
        builder.breakOp(fillMode, "", ZERO);
        needDot = true;
      }
      formatArrayIndices(getArrayIndices(node));
      if (stack.isEmpty()) {
        builder.close();
        return;
      }
    }

    Set<Integer> prefixes = new LinkedHashSet<>();

    // Check if the dot chain has a prefix that looks like a type name, so we can
    // treat the type name-shaped part as a single syntactic unit.
    TypeNameClassifier.typePrefixLength(simpleNames(stack)).ifPresent(prefixes::add);

    // With only one call, keep its leading fields together instead of isolating a lone selector.
    if (callCount == 1 && firstInvocationIndex > 0) {
      prefixes.add(firstInvocationIndex);
    }

    if (prefixes.isEmpty() && items.get(0) instanceof IdentifierTree identifierTree) {
      switch (identifierTree.getName().toString()) {
        case "this", "super" -> prefixes.add(1);
        default -> {}
      }
    }

    Indent chainIndent =
        forceSelectorBreaks || variableInitializer != node0 ? plusFour : ZERO;
    if (!prefixes.isEmpty()) {
      visitDotWithPrefix(items, needDot, prefixes, chainIndent, forceSelectorBreaks);
    } else {
      visitRegularDot(items, needDot, chainIndent, forceSelectorBreaks);
    }

    if (node != null) {
      builder.close();
    }
  }

  /** Emits a chain without a grouped qualifier prefix, aligning selectors when possible. */
  private void visitRegularDot(
      List<ExpressionTree> items,
      boolean needDot,
      Indent chainIndent,
      boolean forceSelectorBreaks) {
    boolean trailingDereferences = items.size() > 1;
    boolean needDot0 = needDot;
    BreakTag firstSelector = genSym();
    boolean alignSelectors =
        !needDot0 && trailingDereferences && items.get(0) instanceof IdentifierTree;
    if (!needDot0) {
      builder.open(
          alignSelectors
              ? Indent.Align.toBreakColumn(firstSelector, maximumAlignment, chainIndent)
              : chainIndent);
    }
    BreakTag currentChainBreak = genSym();
    boolean beforeFirstSelector = true;
    // Keep leading fields with the receiver; forced selector breaks start after the first call.
    boolean hasInvocation = false;
    for (ExpressionTree e : items) {
      if (needDot) {
        if (beforeFirstSelector) {
          builder.breakOp(
              forceSelectorBreaks && hasInvocation ? FillMode.FORCED : INDEPENDENT,
              "", ZERO, Optional.of(firstSelector));
          beforeFirstSelector = false;
        } else {
          builder.breakOp(
              forceSelectorBreaks && hasInvocation ? FillMode.FORCED : FillMode.UNIFIED,
              "",
              ZERO,
              Optional.of(currentChainBreak));
        }
        token(".");
      }
      BreakTag tyargTag = genSym();
      dotExpressionUpToArgs(e, Optional.of(tyargTag));
      Indent tyargIndent = Indent.If.make(tyargTag, plusFour, ZERO);
      BreakTag previousChainBreak = chainBreak;
      chainBreak = currentChainBreak;
      try {
        dotExpressionArgsAndParen(
            e, tyargIndent, (trailingDereferences || needDot) ? plusFour : ZERO,
            forceSelectorBreaks);
      } finally {
        chainBreak = previousChainBreak;
      }
      hasInvocation |= getArrayBase(e) instanceof MethodInvocationTree;
      needDot = true;
    }
    if (!needDot0) {
      builder.close();
    }
  }

  /**
   * Groups type-like or single-call qualifier prefixes while allowing later selectors to break.
   *
   * @param prefixes the final item index of each qualifier group
   */
  private void visitDotWithPrefix(
      List<ExpressionTree> items,
      boolean needDot,
      Collection<Integer> prefixes,
      Indent chainIndent,
      boolean forceSelectorBreaks) {
    // Are there method invocations or field accesses after the prefix?
    boolean trailingDereferences = !prefixes.isEmpty() && getLast(prefixes) < items.size() - 1;

    builder.open(chainIndent);
    for (int times = 0; times < prefixes.size(); times++) {
      builder.open(ZERO);
    }

    Deque<Integer> unconsumedPrefixes = new ArrayDeque<>(ImmutableSortedSet.copyOf(prefixes));
    BreakTag nameTag = genSym();
    for (int i = 0; i < items.size(); i++) {
      ExpressionTree e = items.get(i);
      if (needDot) {
        FillMode fillMode;
        if (!unconsumedPrefixes.isEmpty() && i <= unconsumedPrefixes.peekFirst()) {
          fillMode = INDEPENDENT;
        } else {
          fillMode = forceSelectorBreaks ? FillMode.FORCED : FillMode.UNIFIED;
        }

        builder.breakOp(fillMode, "", ZERO, Optional.of(nameTag));
        token(".");
      }
      BreakTag tyargTag = genSym();
      dotExpressionUpToArgs(e, Optional.of(tyargTag));
      if (!unconsumedPrefixes.isEmpty() && i == unconsumedPrefixes.peekFirst()) {
        builder.close();
        unconsumedPrefixes.removeFirst();
      }

      Indent tyargIndent = Indent.If.make(tyargTag, plusFour, ZERO);
      Indent argsIndent = Indent.If.make(nameTag, plusFour, trailingDereferences ? plusFour : ZERO);
      BreakTag previousChainBreak = chainBreak;
      chainBreak = nameTag;
      try {
        dotExpressionArgsAndParen(e, tyargIndent, argsIndent, forceSelectorBreaks);
      } finally {
        chainBreak = previousChainBreak;
      }

      needDot = true;
    }

    builder.close();
  }

  private static boolean exceedsUnbrokenChainComplexity(ExpressionTree expression) {
    return 1 + 2 * chainCallCount(expression) > MAX_UNBROKEN_COMPLEXITY;
  }

  private static int chainCallCount(ExpressionTree expression) {
    int count = 0;
    while (expression != null) {
      if (expression instanceof MethodInvocationTree || expression instanceof NewClassTree) {
        count++;
      }
      expression = chainReceiver(expression);
    }
    return count;
  }

  // Follow the value carrying the chain, not calls inside arguments, array indices, or cast types.
  private static ExpressionTree chainReceiver(ExpressionTree expression) {
    return switch (expression) {
      case MethodInvocationTree invocation -> getMethodReceiver(invocation);
      case MemberSelectTree select -> select.getExpression();
      case ParenthesizedTree parenthesized -> parenthesized.getExpression();
      case ArrayAccessTree access -> access.getExpression();
      case TypeCastTree cast -> cast.getExpression();
      default -> null;
    };
  }

  private long nestedChainWidthScore(ExpressionTree expression) {
    Tree call = enclosingCall(expression);
    // Include the opening '(' and, for constructors, the 'new ' prefix.
    int prefixWidth = switch (call) {
      case MethodInvocationTree invocation -> syntaxWidth(invocation.getMethodSelect()) + 1;
      case NewClassTree creation -> syntaxWidth(creation.getIdentifier()) + 5;
      case null, default -> 0;
    };
    if (prefixWidth == 0) {
      return 0;
    }
    // Measure from the enclosing call to each selector, excluding the selector's own width.
    // Do not charge sibling arguments again: the enclosing list has already scored them.
    long score = 0;
    while (expression != null) {
      ExpressionTree receiver = chainReceiver(expression);
      if (expression instanceof MethodInvocationTree || expression instanceof MemberSelectTree) {
        int distance = prefixWidth + (receiver == null ? 0 : syntaxWidth(receiver) + 1);
        score += (long) memberLayoutWeight(expression) * distance;
      } else if (expression instanceof NewClassTree) {
        score += (long) memberLayoutWeight(expression) * prefixWidth;
      }
      expression = receiver;
    }
    return score;
  }

  private Tree enclosingCall(ExpressionTree expression) {
    List<? extends ExpressionTree> arguments = switch (argumentOwner) {
      case MethodInvocationTree invocation -> invocation.getArguments();
      case NewClassTree construction -> construction.getArguments();
      case null, default -> List.of();
    };
    // Only a direct argument (possibly parenthesized) pays for the enclosing call's prefix.
    for (ExpressionTree argument : arguments) {
      while (argument instanceof ParenthesizedTree parenthesized) {
        argument = parenthesized.getExpression();
      }
      if (argument == expression) {
        return argumentOwner;
      }
    }
    return null;
  }

  /** Returns the simple names of expressions in a "." chain. */
  private static ImmutableList<String> simpleNames(Deque<ExpressionTree> stack) {
    ImmutableList.Builder<String> simpleNames = ImmutableList.builder();
    OUTER:
    for (ExpressionTree expression : stack) {
      boolean isArray = expression.getKind() == ARRAY_ACCESS;
      expression = getArrayBase(expression);
      switch (expression.getKind()) {
        case MEMBER_SELECT ->
            simpleNames.add(((MemberSelectTree) expression).getIdentifier().toString());
        case IDENTIFIER -> simpleNames.add(((IdentifierTree) expression).getName().toString());
        case METHOD_INVOCATION -> {
          simpleNames.add(getMethodName((MethodInvocationTree) expression).toString());
          break OUTER;
        }
        default -> {
          break OUTER;
        }
      }
      if (isArray) {
        break OUTER;
      }
    }
    return simpleNames.build();
  }

  private void dotExpressionUpToArgs(ExpressionTree expression, Optional<BreakTag> tyargTag) {
    expression = getArrayBase(expression);
    switch (expression.getKind()) {
      case MEMBER_SELECT -> {
        MemberSelectTree fieldAccess = (MemberSelectTree) expression;
        visit(fieldAccess.getIdentifier());
      }
      case METHOD_INVOCATION -> {
        MethodInvocationTree methodInvocation = (MethodInvocationTree) expression;
        if (!methodInvocation.getTypeArguments().isEmpty()) {
          builder.open(plusFour);
          addTypeArguments(methodInvocation.getTypeArguments(), ZERO);
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO, tyargTag);
          builder.close();
        }
        visit(getMethodName(methodInvocation));
      }
      case IDENTIFIER -> visit(((IdentifierTree) expression).getName());
      default -> scan(expression, null);
    }
  }

  /**
   * Returns the base expression of an array access, e.g. given {@code foo[0][0]} returns {@code
   * foo}.
   */
  private static ExpressionTree getArrayBase(ExpressionTree node) {
    while (node instanceof ArrayAccessTree arrayAccessTree) {
      node = arrayAccessTree.getExpression();
    }
    return node;
  }

  private static @Nullable ExpressionTree getMethodReceiver(MethodInvocationTree methodInvocation) {
    ExpressionTree select = methodInvocation.getMethodSelect();
    return select instanceof MemberSelectTree memberSelectTree
        ? memberSelectTree.getExpression()
        : null;
  }

  private void dotExpressionArgsAndParen(
      ExpressionTree expression, Indent tyargIndent, Indent indent, boolean ownsLayout) {
    Deque<ExpressionTree> indices = getArrayIndices(expression);
    expression = getArrayBase(expression);
    switch (expression.getKind()) {
      case METHOD_INVOCATION -> {
        builder.open(tyargIndent);
        MethodInvocationTree methodInvocation = (MethodInvocationTree) expression;
        LayoutContext previousContext = layoutContext;
        if (ownsLayout) {
          layoutContext = LayoutContext.BROKEN_CHAIN;
        }
        try {
          addArguments(methodInvocation, methodInvocation.getArguments(), indent);
        } finally {
          layoutContext = previousContext;
        }
        builder.close();
      }
      default -> {}
    }
    formatArrayIndices(indices);
  }

  /** Lays out one or more array indices. Does not output the expression for the array itself. */
  private void formatArrayIndices(Deque<ExpressionTree> indices) {
    if (indices.isEmpty()) {
      return;
    }
    builder.open(ZERO);
    do {
      token("[");
      builder.breakToFill();
      scan(indices.removeLast(), null);
      token("]");
    } while (!indices.isEmpty());
    builder.close();
  }

  /**
   * Returns all array indices for the given expression, e.g. given {@code foo[0][0]} returns the
   * expressions for {@code [0][0]}.
   */
  private static Deque<ExpressionTree> getArrayIndices(ExpressionTree expression) {
    Deque<ExpressionTree> indices = new ArrayDeque<>();
    while (expression instanceof ArrayAccessTree array) {
      indices.addLast(array.getIndex());
      expression = array.getExpression();
    }
    return indices;
  }

  private void addTypeArguments(List<? extends Tree> typeArguments, Indent plusIndent) {
    if (typeArguments == null || typeArguments.isEmpty()) {
      return;
    }
    token("<");
    builder.open(plusIndent);
    boolean afterFirstToken = false;
    for (Tree typeArgument : typeArguments) {
      if (afterFirstToken) {
        token(",");
        builder.breakToFill(" ");
      }
      scan(typeArgument, null);
      afterFirstToken = true;
    }
    builder.close();
    token(">");
  }

  /**
   * Add arguments to a method invocation or constructor. Authored two-column tables keep their
   * rows; other lists use the shared flat, wrapped, or broken layout plan.
   *
   * @param owner the invocation or constructor that owns the arguments
   * @param arguments the arguments
   * @param plusIndent the extra indent for the arguments
   */
  private void addArguments(Tree owner, List<? extends ExpressionTree> arguments, Indent plusIndent) {
    Tree previousOwner = argumentOwner;
    argumentOwner = owner;
    try {
      emitArguments(arguments, plusIndent);
    } finally {
      argumentOwner = previousOwner;
    }
  }

  private void emitArguments(List<? extends ExpressionTree> arguments, Indent plusIndent) {
    builder.open(ZERO);
    token("(");
    if (!arguments.isEmpty()) {
      if (arguments.size() % 2 == 0 && argumentsAreTabular(arguments) == 2) {
        tabularArgList(arguments);
      } else {
        argList(arguments, plusIndent);
      }
    }
    token(")");
    builder.close();
  }

  private void tabularArgList(List<? extends ExpressionTree> arguments) {
    LayoutContext previousContext = layoutContext;
    layoutContext = layoutContext.inBrokenList();
    try {
      builder.open(plusFour);
      builder.forcedBreak();
      builder.open(ZERO);
      boolean afterFirstToken = false;
      Iterator<? extends ExpressionTree> iterator = arguments.iterator();
      while (iterator.hasNext()) {
        if (afterFirstToken) {
          token(",");
          builder.forcedBreak();
        }
        builder.open(plusFour);
        scan(iterator.next(), null);
        token(",");
        builder.breakOp(" ");
        scan(iterator.next(), null);
        builder.close();
        afterFirstToken = true;
      }
      builder.close();
      builder.close();
    } finally {
      layoutContext = previousContext;
    }
  }

  private void argList(List<? extends ExpressionTree> arguments, Indent plusIndent) {
    ListPlan plan = argumentListPlan(arguments);
    LayoutContext previousContext = layoutContext;
    if (plan.ownsLayout()) {
      layoutContext = layoutContext.inBrokenList();
    }
    try {
      if (plan.layout() == ListLayout.BROKEN) {
        brokenArgList(arguments);
      } else {
        emitArgList(arguments, plusIndent, plan.breakIndex());
      }
    } finally {
      layoutContext = previousContext;
    }
  }

  private void brokenArgList(List<? extends ExpressionTree> arguments) {
    builder.open(plusFour);
    builder.forcedBreak();
    boolean afterFirst = false;
    for (ExpressionTree argument : arguments) {
      if (afterFirst) {
        token(",");
        builder.forcedBreak();
      }
      scan(argument, null);
      afterFirst = true;
    }
    builder.close();
  }

  private void emitArgList(
      List<? extends ExpressionTree> arguments, Indent plusIndent, int breakIndex) {
    List<BreakTag> previousBreaks = new ArrayList<>();
    boolean afterFirstToken = false;
    boolean continuationOpen = false;
    int index = 0;
    for (ExpressionTree argument : arguments) {
      BreakTag currentArgumentBreak = argumentBreak;
      if (afterFirstToken) {
        boolean breakBeforeArgument = index == breakIndex || containsNestedBlock(argument);
        if (breakBeforeArgument && continuationOpen) {
          builder.close();
          continuationOpen = false;
        }
        token(",");
        currentArgumentBreak = genSym();
        if (breakBeforeArgument) {
          builder.open(preservePreviousIndent(previousBreaks, plusFour));
          continuationOpen = true;
          builder.breakOp(
              Doc.FillMode.FORCED, "", ZERO, Optional.of(currentArgumentBreak));
        } else {
          builder.breakOp(
              INDEPENDENT,
              " ",
              preservePreviousIndent(previousBreaks, plusIndent),
              Optional.of(currentArgumentBreak));
        }
        previousBreaks.add(currentArgumentBreak);
      }
      BreakTag previousArgumentBreak = argumentBreak;
      argumentBreak = currentArgumentBreak;
      try {
        scan(argument, null);
      } finally {
        argumentBreak = previousArgumentBreak;
      }
      afterFirstToken = true;
      index++;
    }
    if (continuationOpen) {
      builder.close();
    }
  }

  /** Returns the column count for an authored table, or {@code -1} if the rows do not form a table. */
  private int argumentsAreTabular(List<? extends ExpressionTree> arguments) {
    if (arguments.isEmpty()) {
      return -1;
    }
    List<List<ExpressionTree>> rows = new ArrayList<>();
    PeekingIterator<ExpressionTree> it = Iterators.peekingIterator(arguments.iterator());
    int start0 = actualColumn(it.peek());
    {
      List<ExpressionTree> row = new ArrayList<>();
      row.add(it.next());
      while (it.hasNext() && actualColumn(it.peek()) > start0) {
        row.add(it.next());
      }
      if (!it.hasNext()) {
        return -1;
      }
      if (rowLength(row) <= 1) {
        return -1;
      }
      rows.add(row);
    }
    while (it.hasNext()) {
      List<ExpressionTree> row = new ArrayList<>();
      int start = actualColumn(it.peek());
      if (start != start0) {
        return -1;
      }
      row.add(it.next());
      while (it.hasNext() && actualColumn(it.peek()) > start0) {
        row.add(it.next());
      }
      rows.add(row);
    }
    int size0 = rows.get(0).size();
    // Require matching kinds in the first column and a majority match in later columns, so an
    // ordinary wrapped list is less likely to be mistaken for a deliberate table.
    if (!expressionsAreParallel(rows, 0, rows.size())) {
      return -1;
    }
    for (int i = 1; i < size0; i++) {
      if (!expressionsAreParallel(rows, i, rows.size() / 2 + 1)) {
        return -1;
      }
    }
    // if there are only two rows, they must be the same length
    if (rows.size() == 2) {
      if (size0 == rows.get(1).size()) {
        return size0;
      }
      return -1;
    }
    // With three or more rows, allow a shorter final row.
    for (int i = 1; i < rows.size() - 1; i++) {
      if (size0 != rows.get(i).size()) {
        return -1;
      }
    }
    if (size0 < getLast(rows).size()) {
      return -1;
    }
    return size0;
  }

  private static int rowLength(List<? extends ExpressionTree> row) {
    int size = 0;
    for (ExpressionTree tree : row) {
      if (tree.getKind() != NEW_ARRAY) {
        size++;
        continue;
      }
      NewArrayTree array = (NewArrayTree) tree;
      if (array.getInitializers() == null) {
        size++;
        continue;
      }
      size += rowLength(array.getInitializers());
    }
    return size;
  }

  private Integer actualColumn(ExpressionTree expression) {
    Map<Integer, Integer> positionToColumnMap = builder.getInput().getPositionToColumnMap();
    return positionToColumnMap.get(builder.actualStartColumn(getStartPosition(expression)));
  }

  /** Returns true if {@code atLeastM} of the expressions in the given column are the same kind. */
  private static boolean expressionsAreParallel(
      List<List<ExpressionTree>> rows, int column, int atLeastM) {
    Multiset<Tree.Kind> nodeTypes = HashMultiset.create();
    for (List<? extends ExpressionTree> row : rows) {
      if (column >= row.size()) {
        continue;
      }
      // Treat UnaryTree expressions as their underlying type for the comparison (so, for example
      // -ve and +ve numeric literals are considered the same).
      switch (row.get(column)) {
        case UnaryTree unary -> nodeTypes.add(unary.getExpression().getKind());
        case ExpressionTree expression -> nodeTypes.add(expression.getKind());
      }
    }
    for (Multiset.Entry<Tree.Kind> nodeType : nodeTypes.entrySet()) {
      if (nodeType.getCount() >= atLeastM) {
        return true;
      }
    }
    return false;
  }

  // General helper functions.

  /** Kind of declaration. */
  private enum DeclarationKind {
    NONE,
    FIELD,
    PARAMETER
  }

  /** Declare one variable or variable-like thing. */
  private int declareOne(
      DeclarationKind kind,
      Direction annotationsDirection,
      Optional<ModifiersTree> modifiers,
      Tree type,
      Name name,
      String op,
      String equals,
      Optional<ExpressionTree> initializer,
      Optional<String> trailing,
      Optional<ExpressionTree> receiverExpression,
      Optional<TypeWithDims> typeWithDims) {

    BreakTag typeBreak = genSym();
    BreakTag verticalAnnotationBreak = genSym();

    // If the node is a field declaration, try to output any declaration
    // annotations in-line. If the entire declaration doesn't fit on a single
    // line, fall back to one-per-line.
    boolean isField = kind == DeclarationKind.FIELD;

    if (isField) {
      builder.blankLineWanted(BlankLineWanted.conditional(verticalAnnotationBreak));
    }

    Deque<List<? extends AnnotationTree>> dims =
        new ArrayDeque<>(typeWithDims.isPresent() ? typeWithDims.get().dims() : ImmutableList.of());
    int baseDims = 0;

    // preprocess to separate declaration annotations + modifiers, type annotations

    DeclarationModifiersAndTypeAnnotations declarationAndTypeModifiers =
        modifiers
            .map(m -> splitModifiers(m, m.getAnnotations()))
            .orElse(DeclarationModifiersAndTypeAnnotations.empty());
    builder.open(
        kind == DeclarationKind.PARAMETER && declarationAndTypeModifiers.hasDeclarationAnnotation()
            ? plusFour
            : ZERO);
    {
      List<AnnotationTree> annotations =
          visitModifiers(
              declarationAndTypeModifiers,
              annotationsDirection,
              Optional.of(verticalAnnotationBreak));
      boolean isVar =
          builder.peekToken().get().equals("var")
              && (!name.contentEquals("var") || builder.peekToken(1).get().equals("var"));
      boolean hasType = type != null || isVar;
      builder.open(hasType ? plusFour : ZERO);
      {
        builder.open(ZERO);
        {
          builder.open(ZERO);
          {
            visitAnnotations(annotations, BreakOrNot.NO, BreakOrNot.YES);
            if (isVar) {
              token("var");
            } else if (typeWithDims.isPresent() && typeWithDims.get().node() != null) {
              scan(typeWithDims.get().node(), null);
              int totalDims = dims.size();
              builder.open(plusFour);
              maybeAddDims(dims);
              builder.close();
              baseDims = totalDims - dims.size();
            } else {
              scan(type, null);
            }
          }
          builder.close();

          if (hasType) {
            builder.breakOp(Doc.FillMode.INDEPENDENT, " ", ZERO, Optional.of(typeBreak));
          }

          // conditionally ident the name and initializer +4 if the type spans
          // multiple lines
          builder.open(Indent.If.make(typeBreak, plusFour, ZERO));
          if (receiverExpression.isPresent()) {
            scan(receiverExpression.get(), null);
          } else {
            variableName(name);
          }
          builder.op(op);
        }
        maybeAddDims(dims);
        builder.close();
      }
      builder.close();

      if (initializer.isPresent()) {
        builder.space();
        token(equals);
        if (initializer.get().getKind() == Tree.Kind.NEW_ARRAY
            && ((NewArrayTree) initializer.get()).getType() == null) {
          builder.open(minusFour);
          builder.space();
          initializer.get().accept(this, null);
          builder.close();
        } else {
          builder.open(Indent.If.make(typeBreak, plusFour, ZERO));
          {
            if (initializer.get() instanceof MethodInvocationTree) {
              builder.space();
            } else {
              builder.breakToFill(" ");
            }
            ExpressionTree previousVariableInitializer = variableInitializer;
            variableInitializer = initializer.get();
            try {
              scan(initializer.get(), null);
            } finally {
              variableInitializer = previousVariableInitializer;
            }
          }
          builder.close();
        }
      }
      if (trailing.isPresent() && builder.peekToken().equals(trailing)) {
        builder.guessToken(trailing.get());
      }

      // end of conditional name and initializer indent
      builder.close();
    }
    builder.close();

    if (isField) {
      builder.blankLineWanted(BlankLineWanted.conditional(verticalAnnotationBreak));
    }

    return baseDims;
  }

  private void variableName(Name name) {
    if (name.isEmpty()) {
      token("_");
    } else {
      visit(name);
    }
  }

  private void maybeAddDims(Deque<List<? extends AnnotationTree>> annotations) {
    maybeAddDims(new ArrayDeque<>(), annotations);
  }

  /**
   * The compiler does not always preserve the concrete syntax of annotated array dimensions, and
   * mixed-notation array dimensions. Use look-ahead to preserve the original syntax.
   *
   * <p>It is assumed that any number of regular dimension specifiers ({@code []} with no
   * annotations) may be present in the input.
   *
   * @param dimExpressions an ordered list of dimension expressions (e.g. the {@code 0} in {@code
   *     new int[0]}
   * @param annotations an ordered list of type annotations grouped by dimension (e.g. {@code
   *     [[@A, @B], [@C]]} for {@code int @A [] @B @C []}
   */
  private void maybeAddDims(
      Deque<ExpressionTree> dimExpressions, Deque<List<? extends AnnotationTree>> annotations) {
    boolean lastWasAnnotation = false;
    while (builder.peekToken().isPresent()) {
      switch (builder.peekToken().get()) {
        case "@" -> {
          if (annotations.isEmpty()) {
            return;
          }
          List<? extends AnnotationTree> dimAnnotations = annotations.removeFirst();
          if (dimAnnotations.isEmpty()) {
            continue;
          }
          builder.breakToFill(" ");
          visitAnnotations(dimAnnotations, BreakOrNot.NO, BreakOrNot.NO);
          lastWasAnnotation = true;
        }
        case "[" -> {
          if (lastWasAnnotation) {
            builder.breakToFill(" ");
          } else {
            builder.breakToFill();
          }
          token("[");
          if (!builder.peekToken().get().equals("]")) {
            scan(dimExpressions.removeFirst(), null);
          }
          token("]");
          lastWasAnnotation = false;
        }
        case "." -> {
          if (!builder.peekToken().get().equals(".") || !builder.peekToken(1).get().equals(".")) {
            return;
          }
          if (lastWasAnnotation) {
            builder.breakToFill(" ");
          } else {
            builder.breakToFill();
          }
          builder.op("...");
          lastWasAnnotation = false;
        }
        default -> {
          return;
        }
      }
    }
  }

  private void declareMany(List<VariableTree> fragments, Direction annotationDirection) {
    builder.open(ZERO);

    ModifiersTree modifiers = fragments.get(0).getModifiers();
    Tree type = fragments.get(0).getType();

    visitAndBreakModifiers(
        modifiers, annotationDirection, /* declarationAnnotationBreak= */ Optional.empty());
    builder.open(plusFour);
    builder.open(ZERO);
    TypeWithDims extractedDims = DimensionHelpers.extractDims(type, SortedDims.YES);
    Deque<List<? extends AnnotationTree>> dims = new ArrayDeque<>(extractedDims.dims());
    scan(extractedDims.node(), null);
    int baseDims = dims.size();
    maybeAddDims(dims);
    baseDims = baseDims - dims.size();
    boolean afterFirstToken = false;
    for (VariableTree fragment : fragments) {
      if (afterFirstToken) {
        token(",");
      }
      TypeWithDims fragmentDims =
          variableFragmentDims(afterFirstToken, baseDims, fragment.getType());
      dims = new ArrayDeque<>(fragmentDims.dims());
      builder.breakOp(" ");
      builder.open(ZERO);
      maybeAddDims(dims);
      variableName(fragment.getName());
      maybeAddDims(dims);
      ExpressionTree initializer = fragment.getInitializer();
      if (initializer != null) {
        builder.space();
        token("=");
        builder.open(plusFour);
        builder.breakOp(" ");
        scan(initializer, null);
        builder.close();
      }
      builder.close();
      if (!afterFirstToken) {
        builder.close();
      }
      afterFirstToken = true;
    }
    builder.close();
    token(";");
    builder.close();
  }

  /** Add a list of declarations. */
  private void addBodyDeclarations(
      List<? extends Tree> bodyDeclarations, BracesOrNot braces, FirstDeclarationsOrNot first0) {
    if (bodyDeclarations.isEmpty()) {
      if (braces.isYes()) {
        builder.space();
        tokenBreakTrailingComment("{", plusTwo);
        builder.blankLineWanted(BlankLineWanted.NO);
        builder.open(ZERO);
        if (builder.peekToken().equals(Optional.of(";"))) {
          builder.open(plusTwo);
          dropEmptyDeclarations();
          builder.close();
          builder.forcedBreak();
        }
        token("}", plusTwo);
        builder.close();
      }
    } else {
      if (braces.isYes()) {
        builder.space();
        tokenBreakTrailingComment("{", plusTwo);
        builder.open(ZERO);
      }
      builder.open(plusTwo);
      boolean first = first0.isYes();
      boolean lastOneGotBlankLineBefore = false;
      PeekingIterator<Tree> it = Iterators.peekingIterator(bodyDeclarations.iterator());
      while (it.hasNext()) {
        Tree bodyDeclaration = it.next();
        dropEmptyDeclarations();
        builder.forcedBreak();
        boolean thisOneGetsBlankLineBefore =
            bodyDeclaration.getKind() != VARIABLE || hasJavaDoc(bodyDeclaration);
        if (first) {
          builder.blankLineWanted(PRESERVE);
        } else if (!first && (thisOneGetsBlankLineBefore || lastOneGotBlankLineBefore)) {
          builder.blankLineWanted(YES);
        }
        markForPartialFormat();

        if (bodyDeclaration.getKind() == VARIABLE) {
          visitVariables(
              variableFragments(it, bodyDeclaration), DeclarationKind.FIELD, Direction.VERTICAL);
        } else {
          scan(bodyDeclaration, null);
        }
        first = false;
        lastOneGotBlankLineBefore = thisOneGetsBlankLineBefore;
      }
      dropEmptyDeclarations();
      builder.forcedBreak();
      builder.close();
      builder.forcedBreak();
      markForPartialFormat();
      if (braces.isYes()) {
        builder.blankLineWanted(BlankLineWanted.NO);
        token("}", plusTwo);
        builder.close();
      }
    }
  }

  private void classDeclarationTypeList(
      String token, List<? extends Tree> types, boolean forceClauseBreak) {
    if (types.isEmpty()) {
      return;
    }
    int complexity = 0;
    for (Tree type : types) {
      complexity += memberComplexity(type);
    }
    if (forceClauseBreak || complexity > MAX_UNBROKEN_COMPLEXITY) {
      builder.forcedBreak();
    } else {
      builder.breakToFill(" ");
    }
    builder.open(types.size() > 1 ? plusFour : ZERO);
    token(token);
    builder.space();
    boolean afterFirstToken = false;
    int remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
    for (Tree type : types) {
      int typeComplexity = memberComplexity(type);
      if (afterFirstToken) {
        token(",");
        if (typeComplexity > remainingComplexity) {
          builder.forcedBreak();
          remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
        } else {
          builder.breakToFill(" ");
        }
      }
      scan(type, null);
      remainingComplexity -= typeComplexity;
      afterFirstToken = true;
    }
    builder.close();
  }

  /**
   * The parser expands multi-variable declarations into separate single-variable declarations. All
   * of the fragments in the original declaration have the same start position, so we use that as a
   * signal to collect them and preserve the multi-variable declaration in the output.
   *
   * <p>e.g. {@code int x, y;} is parsed as {@code int x; int y;}.
   */
  private static List<VariableTree> variableFragments(
      PeekingIterator<? extends Tree> it, Tree first) {
    List<VariableTree> fragments = new ArrayList<>();
    if (first.getKind() == VARIABLE) {
      int start = getStartPosition(first);
      fragments.add((VariableTree) first);
      while (it.hasNext()
          && it.peek().getKind() == VARIABLE
          && getStartPosition(it.peek()) == start) {
        fragments.add((VariableTree) it.next());
      }
    }
    return fragments;
  }

  private OptionalInt javadocPosition(Tree bodyDeclaration) {
    int position = ((JCTree) bodyDeclaration).getStartPosition();
    Input.Token token = builder.getInput().getPositionTokenMap().get(position);
    if (token == null) {
      return OptionalInt.empty();
    }
    var toksBefore = token.getToksBefore();
    // toksBefore is in source order. If there are several comments preceding bodyDeclaration, we
    // want the last one that is a javadoc comment.
    for (int i = toksBefore.size() - 1; i >= 0; i--) {
      Input.Tok tok = toksBefore.get(i);
      String text = tok.getText();
      if (text.startsWith("/**") || text.startsWith("///")) {
        return OptionalInt.of(tok.getPosition());
      }
    }
    return OptionalInt.empty();
  }

  /** Does this declaration have javadoc preceding it? */
  private boolean hasJavaDoc(Tree bodyDeclaration) {
    return javadocPosition(bodyDeclaration).isPresent();
  }

  private static Optional<? extends Input.Token> getNextToken(Input input, int position) {
    return Optional.ofNullable(input.getPositionTokenMap().get(position));
  }

  /** Does this list of trees end with the specified token? */
  private boolean hasTrailingToken(Input input, List<? extends Tree> nodes, String token) {
    if (nodes.isEmpty()) {
      return false;
    }
    Tree lastNode = getLast(nodes);
    Optional<? extends Input.Token> nextToken =
        getNextToken(input, getEndPosition(lastNode, getCurrentPath()));
    return nextToken.isPresent() && nextToken.get().getTok().getText().equals(token);
  }

  /**
   * Can a local with a set of modifiers be declared with horizontal annotations? This is currently
   * true if there is at most one parameterless annotation, and no others.
   *
   * @param modifiers the list of {@link ModifiersTree}s
   * @return whether the local can be declared with horizontal annotations
   */
  private static Direction canLocalHaveHorizontalAnnotations(ModifiersTree modifiers) {
    int parameterlessAnnotations = 0;
    for (AnnotationTree annotation : modifiers.getAnnotations()) {
      if (annotation.getArguments().isEmpty()) {
        parameterlessAnnotations++;
      }
    }
    return parameterlessAnnotations <= 1
            && parameterlessAnnotations == modifiers.getAnnotations().size()
        ? Direction.HORIZONTAL
        : Direction.VERTICAL;
  }

  /** Choose horizontal annotations only when every variable annotation is parameterless. */
  private static Direction variableAnnotationDirection(ModifiersTree modifiers) {
    for (AnnotationTree annotation : modifiers.getAnnotations()) {
      if (!annotation.getArguments().isEmpty()) {
        return Direction.VERTICAL;
      }
    }
    return Direction.HORIZONTAL;
  }

  /**
   * Emit a {@link Doc.Token}.
   *
   * @param token the {@link String} to wrap in a {@link Doc.Token}
   */
  private void token(String token) {
    builder.token(
        token,
        Doc.Token.RealOrImaginary.REAL,
        ZERO,
        /* breakAndIndentTrailingComment= */ Optional.empty());
  }

  /**
   * Emit a {@link Doc.Token}.
   *
   * @param token the {@link String} to wrap in a {@link Doc.Token}
   * @param plusIndentCommentsBefore extra indent for comments before this token
   */
  private void token(String token, Indent plusIndentCommentsBefore) {
    builder.token(
        token,
        Doc.Token.RealOrImaginary.REAL,
        plusIndentCommentsBefore,
        /* breakAndIndentTrailingComment= */ Optional.empty());
  }

  /** Emit a {@link Doc.Token}, and breaks and indents trailing javadoc or block comments. */
  final void tokenBreakTrailingComment(String token, Indent breakAndIndentTrailingComment) {
    builder.token(
        token, Doc.Token.RealOrImaginary.REAL, ZERO, Optional.of(breakAndIndentTrailingComment));
  }

  private void markForPartialFormat() {
    if (!inExpression()) {
      builder.markForPartialFormat();
    }
  }

  /**
   * Sync to position in the input. If we've skipped outputting any tokens that were present in the
   * input tokens, output them here and complain.
   *
   * @param node the ASTNode holding the input position
   */
  private void sync(Tree node) {
    builder.sync(((JCTree) node).getStartPosition());
  }

  final BreakTag genSym() {
    return new BreakTag();
  }

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this).add("builder", builder).toString();
  }

  @Override
  public Void visitBindingPattern(BindingPatternTree node, Void unused) {
    sync(node);
    VariableTree variableTree = node.getVariable();
    declareOne(
        DeclarationKind.PARAMETER,
        Direction.HORIZONTAL,
        Optional.of(variableTree.getModifiers()),
        variableTree.getType(),
        variableTree.getName(),
        /* op= */ "",
        /* equals= */ "",
        /* initializer= */ Optional.empty(),
        /* trailing= */ Optional.empty(),
        /* receiverExpression= */ Optional.empty(),
        /* typeWithDims= */ Optional.empty());
    return null;
  }

  @Override
  public Void visitYield(YieldTree node, Void aVoid) {
    sync(node);
    token("yield");
    builder.space();
    scan(node.getValue(), null);
    token(";");
    return null;
  }

  @Override
  public Void visitSwitchExpression(SwitchExpressionTree node, Void aVoid) {
    sync(node);
    visitSwitch(node.getExpression(), node.getCases());
    return null;
  }

  @Override
  public Void visitDefaultCaseLabel(DefaultCaseLabelTree node, Void unused) {
    token("default");
    return null;
  }

  @Override
  public Void visitPatternCaseLabel(PatternCaseLabelTree node, Void unused) {
    scan(node.getPattern(), null);
    return null;
  }

  @Override
  public Void visitConstantCaseLabel(ConstantCaseLabelTree node, Void aVoid) {
    scan(node.getConstantExpression(), null);
    return null;
  }

  private static int patternComplexity(PatternTree pattern) {
    if (pattern instanceof BindingPatternTree binding) {
      return memberComplexity(binding.getVariable());
    }
    if (pattern instanceof DeconstructionPatternTree deconstruction) {
      int nestedComplexity = 0;
      for (PatternTree nested : deconstruction.getNestedPatterns()) {
        nestedComplexity += patternComplexity(nested);
      }
      return 1 + Math.min(2, nestedComplexity);
    }
    return 1;
  }

  @Override
  public Void visitDeconstructionPattern(DeconstructionPatternTree node, Void unused) {
    scan(node.getDeconstructor(), null);
    token("(");
    BreakTag patternStart = genSym();
    builder.open(Indent.Align.toCurrentColumn(maximumAlignment));
    builder.breakOp(INDEPENDENT, "", ZERO, Optional.of(patternStart));
    boolean afterFirstToken = false;
    int remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
    List<BreakTag> previousBreaks = new ArrayList<>(List.of(patternStart));
    for (PatternTree pattern : node.getNestedPatterns()) {
      int complexity = patternComplexity(pattern);
      if (afterFirstToken) {
        token(",");
        BreakTag currentBreak = genSym();
        if (complexity > remainingComplexity) {
          Indent indent =
              preservePreviousIndent(
                  previousBreaks,
                  Indent.Align.toBreakColumn(patternStart, maximumAlignment, plusFour));
          builder.breakOp(FillMode.FORCED, " ", indent, Optional.of(currentBreak));
          remainingComplexity = MAX_UNBROKEN_COMPLEXITY;
        } else {
          Indent indent = preservePreviousIndent(previousBreaks, ZERO);
          builder.breakOp(INDEPENDENT, " ", indent, Optional.of(currentBreak));
        }
        previousBreaks.add(currentBreak);
      }
      scan(pattern, null);
      remainingComplexity -= complexity;
      afterFirstToken = true;
    }
    builder.close();
    token(")");
    return null;
  }

  private void visitJcAnyPattern(JCTree.JCAnyPattern unused) {
    token("_");
  }
}
