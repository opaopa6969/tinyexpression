package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.util.cache.SupplierBoundCache;

public class BooleanVariableMethodParameterParser extends LazyChoice implements TypedVariableParser , BooleanExpression{

  static final SupplierBoundCache<BooleanVariableMethodParameterParser> SINGLETON = new SupplierBoundCache<>(BooleanVariableMethodParameterParser::new);


  public BooleanVariableMethodParameterParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(BooleanPrefixedVariableParser.class), 
          Parser.get(BooleanSuffixedVariableParser.class)
      );
  }

  @Override
  public Optional<ExpressionType> typeAsOptional() {
  return Optional.of(ExpressionType._boolean);
  }
  
}