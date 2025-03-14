package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpression;
import org.unlaxer.tinyexpression.parser.booltype.BooleanFactorParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public abstract class AbstractBooleanExpressionParser extends JavaStyleDelimitedLazyChain implements BooleanExpression , VariableTypeSelectable{

	private static final long serialVersionUID = 1362501275934237988L;

	public AbstractBooleanExpressionParser() {
		super();
	}



  @Override
  public org.unlaxer.parser.Parsers getLazyParsers(boolean withNakedVariable) {
    
    Class<? extends Parser> booleanExpressionParserClazz = 
        withNakedVariable ? BooleanFactorParser.class : StrictTypedBooleanFactorParser.class;
    
    // <BooleanExpression> ::= <BooleanExpression>[('=='|'!='|'&'|'|'|'^')<BooleanExpression>]*
    return
      new Parsers(
          Parser.get(booleanExpressionParserClazz),
          new ZeroOrMore(
            new WhiteSpaceDelimitedChain(
              new Choice(
                Parser.<WordParser>get(()->new EqualEqualParser()),
                Parser.<WordParser>get(()->new NotEqualParser()),
                Parser.<WordParser>get(()->new AndParser()),
                Parser.<WordParser>get(()->new OrParser()),
                Parser.<WordParser>get(()->new XorParser())
              ),
              Parser.get(booleanExpressionParserClazz)
            )
          )
        );
  }
  
}