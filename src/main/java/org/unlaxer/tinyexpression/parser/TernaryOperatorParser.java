package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.bool.BooleanFactorParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;

public class TernaryOperatorParser extends JavaStyleDelimitedLazyChain{

	private static final long serialVersionUID = -6559995208538992563L;

	public TernaryOperatorParser() {
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
	        Parser.get(QuestionParser.class),
	        Parser.get(NumberExpressionParser.class),
	        Parser.get(ColonParser.class),
	        Parser.get(NumberExpressionParser.class)
	      );
	  }

}