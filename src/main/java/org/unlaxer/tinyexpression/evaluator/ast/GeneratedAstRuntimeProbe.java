package org.unlaxer.tinyexpression.evaluator.ast;

import java.lang.reflect.Method;
import java.util.Optional;

import org.unlaxer.tinyexpression.evaluator.p4.P4StrictMatchTypingValidator;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

final class GeneratedAstRuntimeProbe {

  private GeneratedAstRuntimeProbe() {}

  static boolean isAvailable(ClassLoader classLoader) {
    try {
      Class.forName("org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers", false, classLoader);
      Class.forName("org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper", false, classLoader);
      return true;
    } catch (Throwable e) {
      return false;
    }
  }

  static Optional<Object> tryMapAst(String source, ClassLoader classLoader) {
    return tryMapAst(source, classLoader, null);
  }

  static Optional<Object> tryMapAst(String source, ClassLoader classLoader, String preferredAstSimpleName) {
    Optional<Object> mapped = tryMapAstOnce(source, classLoader, preferredAstSimpleName);
    if (mapped.isPresent()) {
      return mapped;
    }
    String withoutComments = TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(source);
    if (!withoutComments.equals(source)) {
      mapped = tryMapAstOnce(withoutComments, classLoader, preferredAstSimpleName);
      if (mapped.isPresent()) {
        return mapped;
      }
    }
    String normalized = TinyExpressionParserCapabilities.trimLeadingJavaStyleDelimiters(source);
    if (!normalized.equals(source)) {
      mapped = tryMapAstOnce(normalized, classLoader, preferredAstSimpleName);
      if (mapped.isPresent()) {
        return mapped;
      }
    }
    String normalizedWithoutComments = TinyExpressionParserCapabilities.trimLeadingJavaStyleDelimiters(withoutComments);
    if (!normalizedWithoutComments.equals(source) && !normalizedWithoutComments.equals(normalized)) {
      mapped = tryMapAstOnce(normalizedWithoutComments, classLoader, preferredAstSimpleName);
      if (mapped.isPresent()) {
        return mapped;
      }
    }
    String normalizedHead = TinyExpressionParserCapabilities.normalizeStructuredHead(normalized);
    if (!normalizedHead.equals(normalized)) {
      mapped = tryMapAstOnce(normalizedHead, classLoader, preferredAstSimpleName);
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
      return tryMapAst(invocationHead, classLoader, preferredAstSimpleName);
    }
    mapped = tryMapAstOnce(normalizedHeadWithoutComments, classLoader, preferredAstSimpleName);
    if (mapped.isPresent()) {
      return mapped;
    }
    String invocationHead = extractInvocationHeadCandidate(source);
    if (invocationHead == null || invocationHead.equals(source)) {
      return Optional.empty();
    }
    return tryMapAst(invocationHead, classLoader, preferredAstSimpleName);
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

  private static Optional<Object> tryMapAstOnce(String source, ClassLoader classLoader, String preferredAstSimpleName) {
    try {
      Class<?> mapperClass = Class.forName(
          "org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper", false, classLoader);
      Object ast;
      if (preferredAstSimpleName == null || preferredAstSimpleName.isBlank()) {
        Method parse = mapperClass.getMethod("parse", String.class);
        ast = parse.invoke(null, source);
      } else {
        try {
          Method parsePreferred = mapperClass.getMethod("parse", String.class, String.class);
          ast = parsePreferred.invoke(null, source, preferredAstSimpleName);
        } catch (NoSuchMethodException ignored) {
          Method parse = mapperClass.getMethod("parse", String.class);
          ast = parse.invoke(null, source);
        }
      }
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
