package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.ParenthesesParser;

public abstract class AbstractStringFactorParser extends LazyChoice implements StringExpression , VariableTypeSelectable{
	
	private static final long serialVersionUID = -3118310290617698589L;
	
	public AbstractStringFactorParser() {
		super();
	}

	public static Class<? extends Parser> NESTED = StringExpressionParser.class;
	
  @Override
  public org.unlaxer.parser.Parsers getLazyParsers(boolean withNakedVariable) {
    
    Parsers parsers = new Parsers();
    
    parsers.add(StringSideEffectExpressionParser.class);
    parsers.add(StringIfExpressionParser.class);
    parsers.add(StrictTypedStringMatchExpressionParser.class);
    parsers.add(StringLiteralParser.class);
    parsers.add(StringVariableParser.class);
    if(withNakedVariable) {
      parsers.add(ExclusiveNakedVariableParser.class);
    }
    parsers.add(new ParenthesesParser(Parser.get(NESTED)));
    parsers.add(TrimParser.class);
    parsers.add(ToUpperCaseParser.class);
    parsers.add(ToLowerCaseParser.class);
    if(MethodInvocationParser.enabled3) {
      parsers.add(MethodInvocationParser.class);
    }
    return parsers;
  }
}