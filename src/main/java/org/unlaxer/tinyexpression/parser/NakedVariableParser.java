package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.util.cache.SupplierBoundCache;

public class NakedVariableParser extends LazyChain implements VariableParser{//implements Expression , BooleanExpression , StringExpression{

	private static final long serialVersionUID = -8533685205048474333L;
	
  static final SupplierBoundCache<NakedVariableParser> SINGLETON = new SupplierBoundCache<>(NakedVariableParser::new);


	public NakedVariableParser() {
		super();
	}

	public NakedVariableParser(Name name) {
		super(name);
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  
	  return 
      new Parsers(
        Parser.get(DollarParser.class),
        Parser.get(IdentifierParser.class)
      );
	}
	
	public String getVariableName(Token thisParserParsed) {
	  
	  return getVariableNameFromNaked(thisParserParsed);
	}
	
  public static String getVariableNameFromNaked(Token thisParserParsed) {
    String variableName = thisParserParsed.tokenString.get().substring(1);
    return variableName; 
  }

  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.empty();
  }
  
  public static NakedVariableParser get() {
    
    return SINGLETON.get();
  }
}
