package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.IgnoreCaseWordParser;

public class FalseTokenParser extends IgnoreCaseWordParser implements BooleanExpression{

	private static final long serialVersionUID = -1725112938929727086L;

	public FalseTokenParser() {
		super("false");
	}
}