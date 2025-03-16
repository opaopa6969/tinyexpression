package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.StaticParser;
import org.unlaxer.parser.elementary.SingleCharacterParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpression;

public class MultipleParser extends SingleCharacterParser implements StaticParser , NumberExpression{

	private static final long serialVersionUID = -5558359079298083248L;
	
	@Override
	public boolean isMatch(char target) {
		return '*' == target; 
	}
	
}