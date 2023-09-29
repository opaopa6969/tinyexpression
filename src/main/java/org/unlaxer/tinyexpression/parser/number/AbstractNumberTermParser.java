package org.unlaxer.tinyexpression.parser.number;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.tinyexpression.parser.DivisionParser;
import org.unlaxer.tinyexpression.parser.MultipleParser;
import org.unlaxer.tinyexpression.parser.VariableTypeSelectable;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public abstract class AbstractNumberTermParser extends JavaStyleDelimitedLazyChain implements NumberExpression , VariableTypeSelectable{
	
	private static final long serialVersionUID = 1430560948407993197L;
	
	public AbstractNumberTermParser() {
		super();
	}
	

  @Override
  public List<Parser> getLazyParsers(boolean withNakedVariable) {

    // <term>::= <factor>[('*'|'/')<factor>]*
    Parsers parsers = new Parsers();
    
    Parser.get(StrictTypedNumberFactorParser.class);
    
    Class<? extends Parser> factorParserClazz = withNakedVariable ?
       NumberFactorParser.class:
       StrictTypedNumberFactorParser.class;
    
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
