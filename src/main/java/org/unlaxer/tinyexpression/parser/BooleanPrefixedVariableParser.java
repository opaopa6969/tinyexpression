package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.cache.SupplierBoundCache;

public class BooleanPrefixedVariableParser extends JavaStyleDelimitedLazyChain implements BooleanExpression , VariableParser {

  private static final long serialVersionUID = -600588538210309122L;
  
  static final SupplierBoundCache<BooleanPrefixedVariableParser> SINGLETON = new SupplierBoundCache<>(BooleanPrefixedVariableParser::new);

  public BooleanPrefixedVariableParser() {
    super();
  }
  
  @Override
  public List<Parser> getLazyParsers() {
    return 
    new Parsers(//
        Parser.get(BooleanTypeHintPrefixParser.class), //0
        Parser.get(NakedVariableParser.class)//1
    );
  }
  
  public String getVariableName(Token thisParserParsed) {
    Token token = thisParserParsed.getChildWithParser(NakedVariableParser.class);//1
    return NakedVariableParser.getVariableNameFromNaked(token);
  }

  @Override
  public Optional<ExpressionType> type() {
    return Optional.of(ExpressionType.bool);
  }
  
  public static BooleanPrefixedVariableParser get() {
    return SINGLETON.get();
  }

}