package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.SingleStringParser;

public class GreaterParser extends SingleStringParser{

	private static final long serialVersionUID = -3429686264535565442L;

	@Override
	public boolean isMatch(String target) {
		return ">".equals(target) ;
	}
}