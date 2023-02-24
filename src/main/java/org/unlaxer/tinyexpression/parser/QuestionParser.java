package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.StaticParser;
import org.unlaxer.parser.elementary.SingleStringParser;

public class QuestionParser extends SingleStringParser implements StaticParser{

	private static final long serialVersionUID = -4216337360898475211L;

	@Override
	public boolean isMatch(String target) {
		return "?".equals(target);
	}
}