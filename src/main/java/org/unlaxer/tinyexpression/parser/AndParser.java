package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpression;

public class AndParser extends WordParser implements BooleanExpression , OpecodeParser{

	private static final long serialVersionUID = -5318484893453396208L;

	public AndParser(){
		super("&");
	}

  @Override
  public Opecode opecode() {
    return Opecodes.booleanAnd;
  }
}