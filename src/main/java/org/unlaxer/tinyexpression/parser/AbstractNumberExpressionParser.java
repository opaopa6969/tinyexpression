package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.RootParserIndicator;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public abstract class AbstractNumberExpressionParser extends JavaStyleDelimitedLazyChain implements RootParserIndicator , NumberExpression , VariableTypeSelectable{
	
  @Override
  public org.unlaxer.parser.Parsers getLazyParsers(boolean withNakedVariable) {
    
    // <expression> ::= <term>[('+'|'-')<term>]*
    Parsers parsers = new Parsers();
    
    Class<? extends Parser> termParserClazz = withNakedVariable ?
      NumberTermParser.class:
      StrictTypedNumberTermParser.class;
    
    parsers.add(termParserClazz);
    
    parsers.add(new ZeroOrMore(
        new WhiteSpaceDelimitedChain(
            new Choice(
              Parser.get(PlusParser.class),
              Parser.get(MinusParser.class)
            ),
            Parser.get(termParserClazz)
          )
    ));
    
    return parsers;
    
  }

  private static final long serialVersionUID = -2100891203224283395L;
	
	Parser parser;

	public AbstractNumberExpressionParser() {
		super();
	}

}