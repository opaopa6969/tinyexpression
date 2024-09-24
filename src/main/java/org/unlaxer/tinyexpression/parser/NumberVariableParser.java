package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.util.cache.SupplierBoundCache;

@SuppressWarnings("serial")
public class NumberVariableParser extends LazyChoice implements RootVariableParser , NumberExpression {
  
  static final SupplierBoundCache<NumberVariableParser> SINGLETON = new SupplierBoundCache<>(NumberVariableParser::new);


  public NumberVariableParser() {
    super();
  }
  
  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(NumberVariableMatchedWithVariableDeclarationParser.class),
          Parser.get(NumberPrefixedVariableParser.class), 
          Parser.get(NumberSuffixedVariableParser.class)
      );
  }
  
  public String getVariableName(Token thisParserParsed) {
    TypedToken<VariableParser> typed = thisParserParsed.typed(VariableParser.class);
    VariableParser parser = typed.getParser();
    return parser.getVariableName(typed);
  }

  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionTypes.number);
  }
  
  public static NumberVariableParser get() {
    return SINGLETON.get();
  }

  @Override
  public Class<? extends RootVariableParser> rootOfTypedVariableParser() {
    return NumberVariableParser.class;
  }

  @Override
  public Class<? extends VariableParser> oneOfTypedVariableParser() {
    return NumberPrefixedVariableParser.class;
  }

  @Override
  public Class<? extends TypeHintVariableParser> typeHintVariableParser() {
    return NumberTypeHintPrefixParser.class;
  }
}