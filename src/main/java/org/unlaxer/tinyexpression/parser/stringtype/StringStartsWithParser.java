package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class StringStartsWithParser extends JavaStyleDelimitedLazyChain 
  implements BooleanExpression , StringMultipleParameterPredicator{
	
	private static final long serialVersionUID = 4961342621488883708L;
	
	public StringStartsWithParser() {
		super();
	}

	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
    //  StringStartsWith:=StringExpression'.startsWith('StringExpression')';
    return
      new Parsers(
        Parser.get(StringExpressionParser.class),
				Parser.get(StartsWithMethodParser.class)
      );
	}


  @Override
  public Class<? extends Parser> parameterParserClass() {
    return StartsWithMethodParser.class;
  }
  
  @Override
  public String predicateMethodString() {
    return "org.unlaxer.util.MultipleParamterStringPredicators.startsWith(";
  }
}