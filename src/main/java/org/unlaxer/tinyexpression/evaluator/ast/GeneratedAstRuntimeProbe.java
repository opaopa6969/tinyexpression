package org.unlaxer.tinyexpression.evaluator.ast;

import java.lang.reflect.Method;
import java.util.Optional;

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
    try {
      Class<?> mapperClass = Class.forName(
          "org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper", false, classLoader);
      Object ast;
      try {
        Method parsePreferred = mapperClass.getMethod("parse", String.class, String.class);
        ast = parsePreferred.invoke(null, source, preferredAstSimpleName);
      } catch (NoSuchMethodException ignored) {
        Method parse = mapperClass.getMethod("parse", String.class);
        ast = parse.invoke(null, source);
      }
      return Optional.ofNullable(ast);
    } catch (Throwable e) {
      return Optional.empty();
    }
  }
}
