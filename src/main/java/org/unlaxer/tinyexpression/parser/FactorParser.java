package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.NoneChildCollectingParser;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.tinyexpression.parser.function.CosParser;
import org.unlaxer.tinyexpression.parser.function.MaxParser;
import org.unlaxer.tinyexpression.parser.function.MinParser;
import org.unlaxer.tinyexpression.parser.function.RandomParser;
import org.unlaxer.tinyexpression.parser.function.SinParser;
import org.unlaxer.tinyexpression.parser.function.SquareRootParser;
import org.unlaxer.tinyexpression.parser.function.TanParser;

public class FactorParser extends NoneChildCollectingParser implements Expression {
	
	private static final long serialVersionUID = 3521391436954908685L;
	
	public FactorParser() {
		super();
	}

	@Override
	public Parser createParser() {
	  
	  return 
	      // <factor>::= <Number>|'('<expression>')'
        new Choice(
//	            new TernaryOperatorParser(expressionParser),
          Parser.get(SideEffectExpressionParser.class),
          Parser.get(IfExpressionParser.class),
          Parser.get(MatchExpressionParser.class),

          Parser.get(NumberParser.class),
          Parser.get(NumberVariableParser.class),
          Parser.get(NakedVariableParser.class),
          
          new ParenthesesParser(Parser.get(ExpressionParser.class)),
          Parser.get(SinParser.class),
          Parser.get(CosParser.class),
          Parser.get(TanParser.class),
          Parser.get(SquareRootParser.class),
          Parser.get(MinParser.class),
          Parser.get(MaxParser.class),
          Parser.get(RandomParser.class),
          Parser.get(FactorOfStringParser.class),
          Parser.get(ToNumParser.class)
        );

	}

}
