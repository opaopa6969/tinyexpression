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
    try {
      Class<?> mapperClass = Class.forName(
          "org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper", false, classLoader);
      Method parse = mapperClass.getMethod("parse", String.class);
      Object ast = parse.invoke(null, source);
      return Optional.ofNullable(ast);
    } catch (Throwable e) {
      return Optional.empty();
    }
  }
}
