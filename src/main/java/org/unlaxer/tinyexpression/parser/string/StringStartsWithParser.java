package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;

public class StringStartsWithParser extends StringMethodExpressionParser implements BooleanExpression{
	
	private static final long serialVersionUID = 4961342621488883708L;
	
	public StringStartsWithParser() {
		super();
	}
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	@Override
	public Parsers getLazyParsers() {
    //  StringStartsWith:=StringExpression'.startsWith('StringExpression')';
    return
      new Parsers(
        Parser.get(StringExpressionParser.class),
        Parser.get(StringStartsWithMethodParser.class)
      );
	}
}