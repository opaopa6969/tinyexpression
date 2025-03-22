package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpression;

public class OrParser extends WordParser implements BooleanExpression , OpecodeParser{

	private static final long serialVersionUID = 1945209342467546020L;

	public OrParser(){
		super("|");
	}

  @Override
  public Opecode opecode() {
    return Opecodes.booleanOr;
  }
}