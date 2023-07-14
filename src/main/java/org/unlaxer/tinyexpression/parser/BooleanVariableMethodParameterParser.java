package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.util.cache.SupplierBoundCache;

public class BooleanVariableMethodParameterParser extends LazyChoice implements VariableParser , BooleanExpression{

  static final SupplierBoundCache<BooleanVariableMethodParameterParser> SINGLETON = new SupplierBoundCache<>(BooleanVariableMethodParameterParser::new);


  public BooleanVariableMethodParameterParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(BooleanPrefixedVariableParser.class), 
          Parser.get(BooleanSuffixedVariableParser.class)
      );
  }
  
  public String getVariableName(Token thisParserParsed) {
    Token choiced = ChoiceInterface.choiced(thisParserParsed);
    if(choiced.parser instanceof BooleanPrefixedVariableParser) {
      return BooleanPrefixedVariableParser.get().getVariableName(choiced);
    }else if(choiced.parser instanceof BooleanSuffixedVariableParser) {
      return BooleanSuffixedVariableParser.get(). getVariableName(choiced);
    }
    throw new IllegalArgumentException();
  }

  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.bool);
  }
  
}