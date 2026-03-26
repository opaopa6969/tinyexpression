package org.unlaxer.tinyexpression.parser.stringtype;

import org.unlaxer.parser.elementary.SingleCharacterParser;
import java.util.Optional;
import org.unlaxer.tinyexpression.parser.Opecode;
import org.unlaxer.tinyexpression.parser.OpecodeParser;
import org.unlaxer.tinyexpression.parser.Opecodes;

public class StringPlusParser extends SingleCharacterParser implements StringExpression , OpecodeParser{

	private static final long serialVersionUID = 4506811816785895944L;

  public static StringPlusParser SINGLETON = new StringPlusParser();

	@Override
	public boolean isMatch(char target) {
		return '+' == target;
	}

  @Override
  public Optional<Opecode> opecode() {
    return Optional.of(Opecodes.stringPlus);
  }
}