package org.unlaxer.tinyexpression.parser.stringtype;

import java.util.Set;

import org.unlaxer.tinyexpression.parser.AbstractStringFactorParser;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypedParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanFactorParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberFactorParser;
import org.unlaxer.util.Singletons;

public class StringFactorParser extends AbstractStringFactorParser implements TypedParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return getLazyParsers(true);
  }

  @Override
  public ExpressionTypes type() {
    return ExpressionTypes.string;
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