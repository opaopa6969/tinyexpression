package org.unlaxer.tinyexpression.parser;

import java.util.List;

public interface Opecode {

  public ExpressionType expressionType();
  public Arity arity();
  public List<ExpressionType> parameters();
  public String name();

}