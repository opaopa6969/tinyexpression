package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.stringtype.StringExpression;
import org.unlaxer.tinyexpression.parser.stringtype.StringPlusParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringTermParser;

public abstract class AbstractStringExpressionParser extends JavaStyleDelimitedLazyChain implements
  StringExpression , VariableTypeSelectable , LeftAndOperatorPlusRights{

	private static final long serialVersionUID = 3057326703009847594L;


	public AbstractStringExpressionParser() {
		super();
	}

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers(boolean withNakedVariable) {
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