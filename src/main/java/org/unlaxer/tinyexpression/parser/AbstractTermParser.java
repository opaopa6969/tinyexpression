package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;

public abstract class AbstractTermParser extends WhiteSpaceDelimitedLazyChain implements Expression , VariableTypeSelectable{
	
	private static final long serialVersionUID = 1430560948407993197L;
	
	public AbstractTermParser() {
		super();
	}
	

  @Override
  public List<Parser> getLazyParsers(boolean withNakedVariable) {

    // <term>::= <factor>[('*'|'/')<factor>]*
    Parsers parsers = new Parsers();
    
    Parser.get(StrictTypedFactorParser.class);
    
    Class<? extends Parser> factorParserClazz = withNakedVariable ?
       FactorParser.class:
       StrictTypedFactorParser.class;
    
    parsers.add(
        Parser.get(
            factorParserClazz 
        )
    );
    parsers.add(new ZeroOrMore(
          new WhiteSpaceDelimitedChain(
            new Choice(
              Parser.get(MultipleParser.class),
              Parser.get(DivisionParser.class)
            ),
            Parser.get(factorParserClazz)
          )
        )
   );
    
    
    return parsers;
  }

}
