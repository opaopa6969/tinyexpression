package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpression;

public class XorParser extends WordParser implements BooleanExpression{

	private static final long serialVersionUID = 8935232964345691717L;

	public XorParser(){
		super("^");
	}
}