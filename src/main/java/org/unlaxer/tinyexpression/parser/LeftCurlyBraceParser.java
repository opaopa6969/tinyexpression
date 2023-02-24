package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.StaticParser;
import org.unlaxer.parser.elementary.SingleCharacterParser;

public class LeftCurlyBraceParser extends SingleCharacterParser implements StaticParser{

	private static final long serialVersionUID = 1613835350166239171L;

	@Override
	public boolean isMatch(char target) {
		return '{' == target; 
	}
	
}