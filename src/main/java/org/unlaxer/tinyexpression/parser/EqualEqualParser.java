package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpression;

public class EqualEqualParser extends WordParser implements BooleanExpression , OpecodeParser{

	private static final long serialVersionUID = -1468277152882221234L;

	public EqualEqualParser() {
		super("==");
	}

  @Override
  public Opecode opecode() {
    return Opecodes.booleanEq;
  }
}