package org.unlaxer.tinyexpression.parser.number;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.DollarParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.RootVariableParser;
import org.unlaxer.tinyexpression.parser.TypeHintVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class NumberVariableParser extends LazyChoice implements RootVariableParser , NumberExpression {

  private static final long serialVersionUID = -6048451001170410L;

  public NumberVariableParser() {
    super();
  }
  
  @Override
  public Parsers getLazyParsers() {
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
    return Optional.of(ExpressionType.number);
  }
  
  public static class NumberVariableMatchedWithVariableDeclarationParser extends LazyChain implements NumberExpression {

    public NumberVariableMatchedWithVariableDeclarationParser() {
      super();
    }

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(//
          Parser.get(DollarParser.class), //0
          Parser.get(NumberVariableDeclarationMatchedTokenParser.class)//1
      );
    }
    
    @TokenExtractor
    static Token getVariableNameToken(Token thisParserParsed) {
      Token token = thisParserParsed.getChildWithParser(NakedVariableParser.class);
      return token;
    }
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