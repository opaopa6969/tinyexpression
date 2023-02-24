package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.WordParser;

public class OrParser extends WordParser implements BooleanExpression{

	private static final long serialVersionUID = 1945209342467546020L;

	public OrParser(){
		super("|");
	}
}