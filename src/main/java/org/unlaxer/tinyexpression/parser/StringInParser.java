package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class StringInParser extends JavaStyleDelimitedLazyChain 
  implements BooleanExpression , StringMultipleParameterPredicator{

	private static final long serialVersionUID = -6734066553844884039L;
	
	List<Parser> parsers;
	
	public StringInParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
	  return
	      //  StringIn:=StringExpression'.in('StringExpression(','StringExpression)*')';
        new Parsers(
          Parser.get(StringExpressionParser.class),
          Parser.get(InMethodParser.class)
        );

	}

  @Override
  public Class<? extends Parser> parameterParserClass() {
    return InMethodParser.class;
  }
  
  @Override
  public String predicateMethodString() {
    return "org.unlaxer.util.MultipleParamterStringPredicators.in(";
  }

}