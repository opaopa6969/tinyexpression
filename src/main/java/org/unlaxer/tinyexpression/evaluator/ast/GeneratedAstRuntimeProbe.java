package org.unlaxer.tinyexpression.evaluator.ast;

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
}

