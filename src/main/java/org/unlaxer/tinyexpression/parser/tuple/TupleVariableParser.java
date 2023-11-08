package org.unlaxer.tinyexpression.parser.tuple;

import java.util.Optional;

import org.unlaxer.Token;
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
import org.unlaxer.tinyexpression.parser.string.StringExpression;
import org.unlaxer.tinyexpression.parser.string.StringPrefixedVariableParser;
import org.unlaxer.tinyexpression.parser.string.StringSuffixedVariableParser;
import org.unlaxer.tinyexpression.parser.string.StringTypeHintPrefixParser;
import org.unlaxer.tinyexpression.parser.string.StringVariableDeclarationMatchedTokenParser;
import org.unlaxer.tinyexpression.parser.string.StringVariableParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class TupleVariableParser extends LazyChoice implements RootVariableParser , TupleExpression{

  public TupleVariableParser() {
    super();
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(//
        Parser.get(StringVariableMatchedWithVariableDeclarationParser.class),
        Parser.get(StringPrefixedVariableParser.class),//
        Parser.get(StringSuffixedVariableParser.class)//
    );
  }
 
  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.string);
  }
  
  public static class StringVariableMatchedWithVariableDeclarationParser extends LazyChain implements StringExpression {

    public StringVariableMatchedWithVariableDeclarationParser() {
      super();
    }

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(//
          Parser.get(DollarParser.class), //0
          Parser.get(StringVariableDeclarationMatchedTokenParser.class)//1
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
    return StringVariableParser.class;
  }

  @Override
  public Class<? extends VariableParser> oneOfTypedVariableParser() {
    return StringPrefixedVariableParser.class;
  }

  @Override
  public Class<? extends TypeHintVariableParser> typeHintVariableParser() {
    return StringTypeHintPrefixParser.class;
  }
}