package org.unlaxer.tinyexpression.model;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.NoneChildParser;

/**
 * this is ephemeral token marker for rendering
 */
public class EphemeralMarked extends NoneChildParser implements Parser{

	private static final long serialVersionUID = 194977899173382981L;

	@Override
	public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
		throw new UnsupportedOperationException("this is marker Parser. unsupported tokenize");
	}

	@Override
	public Parser createParser() {
		return this;
	}

}