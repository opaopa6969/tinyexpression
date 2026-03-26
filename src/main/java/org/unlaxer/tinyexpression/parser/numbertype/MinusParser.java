package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.Optional;
import org.unlaxer.tinyexpression.parser.Opecode;
import org.unlaxer.tinyexpression.parser.OpecodeParser;
import org.unlaxer.tinyexpression.parser.Opecodes;

public class MinusParser extends org.unlaxer.tinyexpression.parser.MinusParser implements OpecodeParser{

	private static final long serialVersionUID = 5176595050631172291L;

	public static MinusParser SINGLETON = new MinusParser();

  @Override
  public Optional<Opecode> opecode() {
    return Optional.of(Opecodes.numberMinus);
  }
}