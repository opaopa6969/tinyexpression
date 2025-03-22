package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpression;

public class NotEqualParser extends WordParser implements BooleanExpression , OpecodeParser{

	private static final long serialVersionUID = 6534427781820258318L;

	public NotEqualParser() {
		super("!=");
	}

  @Override
  public Opecode opecode() {
    return Opecodes.booleanNotEq;
  }
}