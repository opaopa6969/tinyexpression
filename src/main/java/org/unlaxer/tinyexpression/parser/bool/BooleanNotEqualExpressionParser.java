package org.unlaxer.tinyexpression.parser.bool;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.BinaryOperatorParser;
import org.unlaxer.tinyexpression.parser.NotEqualParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class BooleanNotEqualExpressionParser extends JavaStyleDelimitedLazyChain implements BinaryOperatorParser{

	private static final long serialVersionUID = 4968376203603812079L;
	
	
	public BooleanNotEqualExpressionParser() {
		super();
	}
	
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

	@Override
	public Parsers getLazyParsers() {
    return 
      new Parsers(
        Parser.get(BooleanFactorParser.class),
        Parser.get(NotEqualParser.class),
        Parser.get(BooleanFactorParser.class)
      );
	}
}