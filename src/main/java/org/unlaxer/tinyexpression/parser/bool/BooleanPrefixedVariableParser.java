package org.unlaxer.tinyexpression.parser.bool;

import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.cache.SupplierBoundCache;

public class BooleanPrefixedVariableParser extends JavaStyleDelimitedLazyChain implements BooleanExpression , VariableParser {

  private static final long serialVersionUID = -600588538210309122L;
  
  static final SupplierBoundCache<BooleanPrefixedVariableParser> SINGLETON = new SupplierBoundCache<>(BooleanPrefixedVariableParser::new);

  public BooleanPrefixedVariableParser() {
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
        Parser.get(BooleanTypeHintPrefixParser.class), //0
        Parser.get(NakedVariableParser.class)//1
    );
  }
  
  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.bool);
  }
  
  public static BooleanPrefixedVariableParser get() {
    return SINGLETON.get();
  }

}