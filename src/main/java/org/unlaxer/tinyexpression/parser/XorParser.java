package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpression;

public class XorParser extends WordParser implements BooleanExpression , OpecodeParser{

	private static final long serialVersionUID = 8935232964345691717L;

	public XorParser(){
		super("^");
	}

  @Override
  public Opecode opecode() {
    return Opecodes.booleanXor;
  }
}