package org.unlaxer.tinyexpression.parser;


import org.unlaxer.Parsed;
import org.unlaxer.Parsed.Status;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.NoneChildParser;

public class FormulaParser extends NoneChildParser {
	
	private static final long serialVersionUID = -7049405933791251671L;

//	public static final FormulaParser SINGLETON = new FormulaParser();
	
//  public NumberExpressionParser expressionParser;
  public TinyExpressionParser expressionParser;

	public FormulaParser() {
		super();
    expressionParser = Parser.get(TinyExpressionParser.class);
	}

	@Override
	public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
		Parsed parsed = expressionParser.parse(parseContext);
		if(false == parsed.isSucceeded() || parseContext.allConsumed()){
			return parsed;
		}
		return new Parsed(parsed.getRootToken(),Status.failed);
	}

	@Override
	public Parser createParser() {
		return this;
	}
	
}