package org.unlaxer.tinyexpression.parser;

import java.util.List;

public final class TinyExpressionKeywords {

  public static final String IF = "if";
  public static final String MATCH = "match";
  public static final String CALL = "call";
  public static final String EXTERNAL = "external";
  public static final String INTERNAL = "internal";
  public static final String VAR = "var";
  public static final String VARIABLE = "variable";

  public static final List<String> METHOD_INVOCATION_HEADS = List.of(CALL, EXTERNAL, INTERNAL);
  public static final List<String> VARIABLE_DECLARATION_HEADS = List.of(VARIABLE, VAR);

  private TinyExpressionKeywords() {}
}
