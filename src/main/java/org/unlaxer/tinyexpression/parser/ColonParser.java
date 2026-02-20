package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.StaticParser;
import org.unlaxer.parser.elementary.SingleStringParser;

public class ColonParser extends SingleStringParser implements StaticParser{

	private static final long serialVersionUID = 4677043480156536908L;

	@Override
	public boolean isMatch(String target) {
		return ":".equals(target);
	}
}