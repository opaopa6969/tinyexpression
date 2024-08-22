package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.cache.SupplierBoundCache;

public class BooleanSuffixedVariableParser extends JavaStyleDelimitedLazyChain implements BooleanExpression , VariableParser {

  private static final long serialVersionUID = -1060485382103097042L;
  
  static final SupplierBoundCache<BooleanSuffixedVariableParser> SINGLETON = new SupplierBoundCache<>(BooleanSuffixedVariableParser::new);

  public BooleanSuffixedVariableParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(NakedVariableParser.class), //
          Parser.get(BooleanTypeHintSuffixParser.class)//
      );
  }
  
  @TokenExtractor
  public static Token getVariableNameAsToken(Token thisParserParsed) {
    Token token = thisParserParsed.getChildWithParser(NakedVariableParser.class);
    return token;
  }
  
  @Override
  public Optional<ExpressionTypes> typeAsOptional() {
    return Optional.of(ExpressionTypes._boolean);
  }
  
  
  public static BooleanSuffixedVariableParser get() {

    return SINGLETON.get();
  }

}