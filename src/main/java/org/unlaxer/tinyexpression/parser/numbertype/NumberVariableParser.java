package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.RootVariableParser;
import org.unlaxer.tinyexpression.parser.TypeHintVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
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
  
//  public static class FloatVariableParser extends NumberVariableParser{
//    @Override
//    public Optional<ExpressionType> typeAsOptional() {
//      return Optional.of(ExpressionTypes._float);
//    }
//  }
//  
//  public static class ShortVariableParser extends NumberVariableParser{
//    @Override
//    public Optional<ExpressionType> typeAsOptional() {
//      return Optional.of(ExpressionTypes._short);
//    }
//  }
//  
//  public static class ByteVariableParser extends NumberVariableParser{
//    @Override
//    public Optional<ExpressionType> typeAsOptional() {
//      return Optional.of(ExpressionTypes._byte);
//    }
//  }
//  
//  public static class IntVariableParser extends NumberVariableParser{
//    @Override
//    public Optional<ExpressionType> typeAsOptional() {
//      return Optional.of(ExpressionTypes._int);
//    }
//  }
//  
//  public static class LongVariableParser extends NumberVariableParser{
//    @Override
//    public Optional<ExpressionType> typeAsOptional() {
//      return Optional.of(ExpressionTypes._long);
//    }
//  }
//  
//  public static class DoubleVariableParser extends NumberVariableParser{
//    @Override
//    public Optional<ExpressionType> typeAsOptional() {
//      return Optional.of(ExpressionTypes._double);
//    }
//  }
//  
//  public static class BigDecimalVariableParser extends NumberVariableParser{
//    @Override
//    public Optional<ExpressionType> typeAsOptional() {
//      return Optional.of(ExpressionTypes.bigDecimal);
//    }
//  }
//  
//  public static class BigIntegerVariableParser extends NumberVariableParser{
//    @Override
//    public Optional<ExpressionType> typeAsOptional() {
//      return Optional.of(ExpressionTypes.bigInteger);
//    }
//  }
}