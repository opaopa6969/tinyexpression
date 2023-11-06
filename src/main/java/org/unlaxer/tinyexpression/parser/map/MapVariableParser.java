package org.unlaxer.tinyexpression.parser.map;

import java.util.List;
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
import org.unlaxer.util.annotation.TokenExtractor;

public class MapVariableParser extends LazyChoice implements RootVariableParser , MapExpression{

  public MapVariableParser() {
    super();
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(//
        Parser.get(MapVariableMatchedWithVariableDeclarationParser.class),
        Parser.get(MapPrefixedVariableParser.class),//
        Parser.get(MapSuffixedVariableParser.class)//
    );
  }
 
  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.string);
  }
  
  public static class MapVariableMatchedWithVariableDeclarationParser extends LazyChain implements MapExpression {

    public MapVariableMatchedWithVariableDeclarationParser() {
      super();
    }

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(//
          Parser.get(DollarParser.class), //0
          Parser.get(MapVariableDeclarationMatchedTokenParser.class)//1
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
    return MapVariableParser.class;
  }

  @Override
  public Class<? extends VariableParser> oneOfTypedVariableParser() {
    return MapPrefixedVariableParser.class;
  }

  @Override
  public Class<? extends TypeHintVariableParser> typeHintVariableParser() {
    return MapTypeHintPrefixParser.class;
  }
}