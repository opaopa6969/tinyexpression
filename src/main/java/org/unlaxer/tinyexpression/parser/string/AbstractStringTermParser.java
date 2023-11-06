package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.tinyexpression.parser.SliceParser;
import org.unlaxer.tinyexpression.parser.VariableTypeSelectable;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public abstract class AbstractStringTermParser extends JavaStyleDelimitedLazyChain implements StringExpression , VariableTypeSelectable{

	private static final long serialVersionUID = 1742165276514464092L;
	

	public AbstractStringTermParser() {
		super();
	}



  @Override
  public Parsers getLazyParsers(boolean withNakedVariable) {
    
    return 
        
        withNakedVariable ? 
    
    new Parsers(
        Parser.get(StringFactorParser.class),
        new ZeroOrMore(
          Parser.get(SliceParser.class)
        )
      ):
        
    new Parsers(
        Parser.get(StrictTypedStringFactorParser.class),
        new ZeroOrMore(
          Parser.get(SliceParser.class)
        )
      );
  }


}