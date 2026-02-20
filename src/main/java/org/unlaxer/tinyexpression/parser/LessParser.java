package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.SingleStringParser;

public class LessParser extends SingleStringParser{

	private static final long serialVersionUID = 6786060019358952013L;

	@Override
	public boolean isMatch(String target) {
		return "<".equals(target) ;
	}
}