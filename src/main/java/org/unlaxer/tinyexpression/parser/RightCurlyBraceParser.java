package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.StaticParser;
import org.unlaxer.parser.elementary.SingleCharacterParser;

public class RightCurlyBraceParser extends SingleCharacterParser implements StaticParser{

	private static final long serialVersionUID = 8333338950450562877L;

	@Override
	public boolean isMatch(char target) {
		return '}' == target; 
	}
	
}