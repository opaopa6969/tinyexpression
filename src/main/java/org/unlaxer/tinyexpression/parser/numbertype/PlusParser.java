package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.parser.elementary.SingleCharacterParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.Opecode;
import org.unlaxer.tinyexpression.parser.OpecodeParser;
import org.unlaxer.tinyexpression.parser.Opecodes;

public class PlusParser extends SingleCharacterParser implements NumberExpression  , OpecodeParser{

	private static final long serialVersionUID = -2284625778872306935L;

	public static PlusParser SINGLETON = new PlusParser();

	@Override
	public boolean isMatch(char target) {
		return '+' == target;
	}

  @Override
  public ExpressionType expressionType() {
    return ExpressionTypes.number;
  }

  @Override
  public Opecode opecode() {
    return Opecodes.numberPlus;
  }
}