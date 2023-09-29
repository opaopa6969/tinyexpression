package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.StaticParser;
import org.unlaxer.parser.elementary.SingleCharacterParser;
import org.unlaxer.tinyexpression.parser.number.NumberExpression;

public class DivisionParser extends SingleCharacterParser implements StaticParser , NumberExpression{

	private static final long serialVersionUID = -1463434347426081506L;
	
	@Override
	public boolean isMatch(char target) {
		return '/' == target; 
	}
	
}