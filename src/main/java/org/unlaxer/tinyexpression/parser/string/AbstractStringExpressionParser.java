package org.unlaxer.tinyexpression.parser.string;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.tinyexpression.parser.VariableTypeSelectable;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public abstract class AbstractStringExpressionParser extends JavaStyleDelimitedLazyChain implements StringExpression , VariableTypeSelectable{

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


}