package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.tinyexpression.parser.AbstractStringFactorParser.StrictTypedStringFactorParser;
import org.unlaxer.tinyexpression.parser.AbstractStringFactorParser.StringFactorParser;

public abstract class AbstractStringTermParser extends WhiteSpaceDelimitedLazyChain implements StringExpression , VariableTypeSelectable{

	private static final long serialVersionUID = 1742165276514464092L;
	

	public AbstractStringTermParser() {
		super();
	}



  @Override
  public List<Parser> getLazyParsers(boolean withNakedVariable) {
    
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
  
  public static class StringTermParser extends AbstractStringTermParser{

    @Override
    public boolean hasNakedVariableParser() {
      return true;
    }

    @Override
    public List<Parser> getLazyParsers() {
      return getLazyParsers(true);
    }
    
  }
  
  public static class StrictTypedStringTermParser extends AbstractStringTermParser{

    @Override
    public boolean hasNakedVariableParser() {
      return false;
    }

    @Override
    public List<Parser> getLazyParsers() {
      return getLazyParsers(false);
    }
    
  }


}