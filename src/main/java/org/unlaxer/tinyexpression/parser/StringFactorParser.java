package org.unlaxer.tinyexpression.parser;

import java.util.Set;

import org.unlaxer.util.Singletons;

public class StringFactorParser extends AbstractStringFactorParser implements TypedParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return getLazyParsers(false);
  }

  @Override
  public ExpressionType type() {
    return ExpressionType.string;
  }

  @Override
  public Set<TypedParser> allTypes() {
    return allTypes;
  }

  @Override
  public Set<TypedParser> otherTypes() {
    return otherTypes;
  }
  
  static Set<TypedParser> allTypes = Set.of(
      Singletons.get(NumberFactorParser.class),
      Singletons.get(StringFactorParser.class),
      Singletons.get(BooleanFactorParser.class)
  );
  
  static Set<TypedParser> otherTypes = Set.of(
      Singletons.get(NumberFactorParser.class),
      Singletons.get(BooleanFactorParser.class)
  );

  
}