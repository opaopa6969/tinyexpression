package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.RootParserIndicator;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public abstract class AbstractExpressionParser extends JavaStyleDelimitedLazyChain implements RootParserIndicator , Expression , VariableTypeSelectable{
	
  @Override
  public List<Parser> getLazyParsers(boolean withNakedVariable) {
    
    // <expression> ::= <term>[('+'|'-')<term>]*
    Parsers parsers = new Parsers();
    
    Class<? extends Parser> termParserClazz = withNakedVariable ?
      TermParser.class:
      StrictTypedTermParser.class;
    
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

	public AbstractExpressionParser() {
		super();
	}

}