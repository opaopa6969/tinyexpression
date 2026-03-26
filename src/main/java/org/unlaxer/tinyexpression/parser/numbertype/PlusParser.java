package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.Optional;
import org.unlaxer.tinyexpression.parser.Opecode;
import org.unlaxer.tinyexpression.parser.OpecodeParser;
import org.unlaxer.tinyexpression.parser.Opecodes;

public class PlusParser extends org.unlaxer.tinyexpression.parser.PlusParser implements OpecodeParser{

	private static final long serialVersionUID = -2284625778872306935L;

	public static PlusParser SINGLETON = new PlusParser();

  @Override
  public Optional<Opecode> opecode() {
    return Optional.of(Opecodes.numberPlus);
  }
}