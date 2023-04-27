package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.ParenthesesParser;

public class BooleanExpressionParser extends LazyChoice implements BooleanExpression{

	private static final long serialVersionUID = -3195226739862127225L;
	
	public BooleanExpressionParser() {
		super();
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  return 
	      // BooleanExpression ::= 
	      //    | 'true'
	      //    | 'false'
	      //    | 'not(' BooleanClause ')'
	      //    | '(' BooleanClause ')'
	      //    | Expression '==' Expression 
	      //    | Expression '!=' Expression 
	      //    | Expression '>=' Expression 
	      //    | Expression '<=' Expression 
	      //    | Expression '>' Expression 
	      //    | Expression '<' Expression 
	      //    | BooleanExpressionOfString
	      //    | Variable
        new Parsers(
          Parser.get(TrueTokenParser.class),
          Parser.get(FalseTokenParser.class),
          Parser.get(InTimeRangeParser.class),
          Parser.get(SideEffectBooleanExpressionParser.class),
          Parser.get(SideEffectStringToBooleanExpressionParser.class),
          Parser.get(NotBooleanExpressionParser.class),
          new ParenthesesParser(Parser.get(BooleanClauseParser.class)),
          Parser.get(IsPresentParser.class),
          Parser.get(EqualEqualExpressionParser.class),
          Parser.get(NotEqualExpressionParser.class),
          Parser.get(GreaterOrEqualExpressionParser.class),
          Parser.get(LessOrEqualExpressionParser.class),
          Parser.get(GreaterExpressionParser.class),
          Parser.get(LessExpressionParser.class),
          Parser.get(BooleanExpressionOfStringParser.class),
          Parser.get(BooleanVariableParser.class),
          Parser.get(NakedVariableParser.class)
        );
	}
}