package org.unlaxer.tinyexpression.evaluator.ast;

import java.util.Optional;

import org.unlaxer.tinyexpression.evaluator.p4.P4StrictMatchTypingValidator;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

final class GeneratedAstRuntimeProbe {

  private GeneratedAstRuntimeProbe() {}

  static boolean isAvailable(ClassLoader classLoader) {
    // These classes are generated and compiled as part of this artifact. The supplied
    // classLoader belongs to the user formula and may intentionally be isolated from
    // application classes, so it must not be used to probe the built-in P4 runtime.
    return TinyExpressionP4AST.class != null;
  }

  static Optional<Object> tryMapAst(String source, ClassLoader classLoader) {
    return tryMapAst(source, classLoader, null);
  }

  /**
   * 生成 P4 文法のバックトラックは深いネストで指数的になり得るため (issue #19)、
   * プローブ全体 (ソース変種の再試行を含む) に時間予算を設ける。予算超過時は
   * empty を返し、呼び出し側は legacy parser へフォールバックする。
   * システムプロパティ {@code tinyexpression.p4.probe.timeout.millis} で調整可能
   * (デフォルト 5000ms、0 以下で無効)。
   */
  static Optional<Object> tryMapAst(String source, ClassLoader classLoader, String preferredAstSimpleName) {
    return tryMapAst(source, classLoader, preferredAstSimpleName, probeDeadlineNanos());
  }

  private static long probeDeadlineNanos() {
    long timeoutMillis = Long.getLong("tinyexpression.p4.probe.timeout.millis", 5000L);
    if (timeoutMillis <= 0L) {
      return 0L;
    }
    return System.nanoTime() + timeoutMillis * 1_000_000L;
  }

  private static Optional<Object> tryMapAst(
      String source, ClassLoader classLoader, String preferredAstSimpleName, long deadlineNanos) {
    Optional<Object> mapped = tryMapAstOnce(source, classLoader, preferredAstSimpleName, deadlineNanos);
    if (mapped.isPresent()) {
      return mapped;
    }
    String withoutComments = TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(source);
    if (!withoutComments.equals(source)) {
      mapped = tryMapAstOnce(withoutComments, classLoader, preferredAstSimpleName, deadlineNanos);
      if (mapped.isPresent()) {
        return mapped;
      }
    }
    String normalized = TinyExpressionParserCapabilities.trimLeadingJavaStyleDelimiters(source);
    if (!normalized.equals(source)) {
      mapped = tryMapAstOnce(normalized, classLoader, preferredAstSimpleName, deadlineNanos);
      if (mapped.isPresent()) {
        return mapped;
      }
    }
    String normalizedWithoutComments = TinyExpressionParserCapabilities.trimLeadingJavaStyleDelimiters(withoutComments);
    if (!normalizedWithoutComments.equals(source) && !normalizedWithoutComments.equals(normalized)) {
      mapped = tryMapAstOnce(normalizedWithoutComments, classLoader, preferredAstSimpleName, deadlineNanos);
      if (mapped.isPresent()) {
        return mapped;
      }
    }
    String normalizedHead = TinyExpressionParserCapabilities.normalizeStructuredHead(normalized);
    if (!normalizedHead.equals(normalized)) {
      mapped = tryMapAstOnce(normalizedHead, classLoader, preferredAstSimpleName, deadlineNanos);
      if (mapped.isPresent()) {
        return mapped;
      }
    }
    String normalizedHeadWithoutComments = TinyExpressionParserCapabilities.normalizeStructuredHead(normalizedWithoutComments);
    if (normalizedHeadWithoutComments.equals(source)
        || normalizedHeadWithoutComments.equals(normalized)
        || normalizedHeadWithoutComments.equals(normalizedHead)) {
      String invocationHead = extractInvocationHeadCandidate(source);
      if (invocationHead == null || invocationHead.equals(source)) {
        return Optional.empty();
      }
      return tryMapAst(invocationHead, classLoader, preferredAstSimpleName, deadlineNanos);
    }
    mapped = tryMapAstOnce(normalizedHeadWithoutComments, classLoader, preferredAstSimpleName, deadlineNanos);
    if (mapped.isPresent()) {
      return mapped;
    }
    String invocationHead = extractInvocationHeadCandidate(source);
    if (invocationHead == null || invocationHead.equals(source)) {
      return Optional.empty();
    }
    return tryMapAst(invocationHead, classLoader, preferredAstSimpleName, deadlineNanos);
  }

  private static String extractInvocationHeadCandidate(String source) {
    if (source == null || source.isBlank()) {
      return null;
    }
    String trimmed = TinyExpressionParserCapabilities.trimLeadingJavaStyleDelimiters(source).stripLeading();
    if (!(trimmed.startsWith("call ")
        || trimmed.startsWith("internal ")
        || trimmed.startsWith("external "))) {
      return null;
    }
    int newline = trimmed.indexOf('\n');
    if (newline < 0) {
      return null;
    }
    String head = trimmed.substring(0, newline).strip();
    return head.isEmpty() ? null : head;
  }

  private static Optional<Object> tryMapAstOnce(
      String source, ClassLoader classLoader, String preferredAstSimpleName, long deadlineNanos) {
    if (deadlineNanos > 0L && System.nanoTime() > deadlineNanos) {
      return Optional.empty();
    }
    try {
      // Delegate to P4PreferredAstMapper.parseByAstSimpleName so that
      // ScopeStore.registerDispatcher is called on the ParseContext before parsing,
      // preventing "transaction nest is illegal" errors that occur when the dispatcher
      // is absent from the generated TinyExpressionP4Mapper.parse method.
      Object ast = P4PreferredAstMapper.parseByAstSimpleName(source, preferredAstSimpleName, deadlineNanos);
      if (ast != null
          && preferredAstSimpleName != null
          && !preferredAstSimpleName.isBlank()
          && !preferredAstSimpleName.equals(ast.getClass().getSimpleName())) {
        return Optional.empty();
      }
      if (ast instanceof TinyExpressionP4AST typedAst
          && P4StrictMatchTypingValidator.firstViolation(typedAst, source).isPresent()) {
        return Optional.empty();
      }
      return Optional.ofNullable(ast);
    } catch (Throwable e) {
      return Optional.empty();
    }
  }

}
