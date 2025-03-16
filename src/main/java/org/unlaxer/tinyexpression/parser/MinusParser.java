package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.SingleCharacterParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpression;

public class MinusParser extends SingleCharacterParser implements NumberExpression{
	
	private static final long serialVersionUID = 5176595050631172291L;
	
	public static MinusParser SINGLETON = new MinusParser();

	@Override
	public boolean isMatch(char target) {
		return '-' == target; 
	}
	
}