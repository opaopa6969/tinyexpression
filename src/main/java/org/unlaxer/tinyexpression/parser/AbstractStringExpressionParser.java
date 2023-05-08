package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.tinyexpression.parser.AbstractStringTermParser.StrictTypedStringTermParser;
import org.unlaxer.tinyexpression.parser.AbstractStringTermParser.StringTermParser;

public abstract class AbstractStringExpressionParser extends WhiteSpaceDelimitedLazyChain implements StringExpression , VariableTypeSelectable{

	private static final long serialVersionUID = 3057326703009847594L;
	
	
	public AbstractStringExpressionParser() {
		super();
	}

  @Override
  public List<Parser> getLazyParsers(boolean withNakedVariable) {
    return
        withNakedVariable ? 
        // StringExpression:=StringTerm('+'StringTerm)*;
          new Parsers(
            Parser.get(StringTermParser.class),
            new ZeroOrMore(
              new Choice(
                Parser.get(StringPlusParser.class),
                Parser.get(StringTermParser.class)
              )
            )
          ):
          new Parsers(
              Parser.get(StrictTypedStringTermParser.class),
              new ZeroOrMore(
                new Choice(
                  Parser.get(StringPlusParser.class),
                  Parser.get(StrictTypedStringTermParser.class)
                )
              )
            );

  }
  
  public static class StringExpressionParser extends AbstractStringExpressionParser{

    @Override
    public boolean hasNakedVariableParser() {
      return true;
    }

    @Override
    public List<Parser> getLazyParsers() {
      return getLazyParsers(true);
    }
    
  }
  
  public static class StrictTypedStringExpressionParser extends AbstractStringExpressionParser{

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