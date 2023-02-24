package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.SingleCharacterParser;

public class StringPlusParser extends SingleCharacterParser implements StringExpression {
	
	private static final long serialVersionUID = 4506811816785895944L;
	
  public static StringPlusParser SINGLETON = new StringPlusParser();

	@Override
	public boolean isMatch(char target) {
		return '+' == target; 
	}
}