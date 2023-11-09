package org.unlaxer.tinyexpression.parser.map;

import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

@SuppressWarnings("serial")
public class MapSuffixedVariableParser extends JavaStyleDelimitedLazyChain implements MapExpression ,VariableParser{

  public MapSuffixedVariableParser() {
    super();
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(//
        Parser.get(NakedVariableParser.class), //0
        Parser.get(MapTypeHintSuffixParser.class)//1
    );
  }

  
  @TokenExtractor
  static Token getVariableNameToken(Token thisParserParsed) {
    Token token = thisParserParsed.getChildWithParser(NakedVariableParser.class);
    return token;
  }

  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.map);
  }

}