package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpression;

public class AndParser extends WordParser implements BooleanExpression{

	private static final long serialVersionUID = -5318484893453396208L;
	
	public AndParser(){
		super("&");
	}
}