package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.*;

import org.unlaxer.parser.StaticParser;
import org.unlaxer.parser.elementary.SingleCharacterParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import java.util.Optional;
import org.unlaxer.tinyexpression.parser.Opecode;
import org.unlaxer.tinyexpression.parser.OpecodeParser;
import org.unlaxer.tinyexpression.parser.Opecodes;

public class NumberMultipleParser extends SingleCharacterParser implements StaticParser , NumberExpression , OpecodeParser{

	private static final long serialVersionUID = -5558359079298083248L;

	@Override
	public boolean isMatch(char target) {
		return '*' == target;
	}

  @Override
  public ExpressionType expressionType() {
    return ExpressionTypes.number;
  }

  @Override
  public Optional<Opecode> opecode() {
    return Optional.of(Opecodes.numberMultiple);
  }

}