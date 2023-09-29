package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.IgnoreCaseWordParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;

public class TrueTokenParser extends IgnoreCaseWordParser implements BooleanExpression{

	private static final long serialVersionUID = -7628578328058106365L;

	public TrueTokenParser() {
		super("true");
	}
}