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
  public List<Parser> getLazyParsers(boolean withNakedVariable) {
    
    Parsers parsers = new Parsers();
    
    parsers.add(Parser.get(StringLiteralParser.class));
    parsers.add(Parser.get(StringVariableParser.class));
    if(withNakedVariable) {
      parsers.add(Parser.get(NakedVariableParser.class));
    }
    parsers.add(new ParenthesesParser(Parser.get(NESTED)));
    parsers.add(Parser.get(TrimParser.class));
    parsers.add(Parser.get(ToUpperCaseParser.class));
    parsers.add(Parser.get(ToLowerCaseParser.class));
    return parsers;
  }
}