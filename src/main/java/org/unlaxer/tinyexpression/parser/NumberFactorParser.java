package org.unlaxer.tinyexpression.parser;

import java.util.Set;

import org.unlaxer.util.Singletons;

public class NumberFactorParser extends AbstractNumberFactorParser implements TypedParser{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return getLazyParsers(false);
  }
  
  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }

  @Override
  public ExpressionType type() {
    return ExpressionType.number;
  }
  

  @Override
  public Set<TypedParser> allTypes() {
    
    return allTypes;
  }
  
  static Set<TypedParser> allTypes = Set.of(
      Singletons.get(NumberFactorParser.class),
      Singletons.get(StringFactorParser.class),
      Singletons.get(BooleanFactorParser.class)
  );

  @Override
  public Set<TypedParser> otherTypes() {
    return otherTypes;
  }
  
  static Set<TypedParser> otherTypes = Set.of(
      Singletons.get(StringFactorParser.class),
      Singletons.get(BooleanFactorParser.class)
  );

}