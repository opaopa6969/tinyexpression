package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.SingleStringParser;

public class DollarParser extends SingleStringParser {


	private static final long serialVersionUID = 6319688983820990763L;

	@Override
	public boolean isMatch(String target) {
		return "$".equals(target);
	}
}