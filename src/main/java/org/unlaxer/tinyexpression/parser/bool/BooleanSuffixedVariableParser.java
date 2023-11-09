package org.unlaxer.tinyexpression.parser.bool;

import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
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
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }


  @Override
  public Parsers getLazyParsers() {
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
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.bool);
  }
  
  
  public static BooleanSuffixedVariableParser get() {

    return SINGLETON.get();
  }

}