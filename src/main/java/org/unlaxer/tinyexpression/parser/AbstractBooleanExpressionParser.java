package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.ParenthesesParser;

public abstract class AbstractBooleanExpressionParser extends LazyChoice implements BooleanExpression , VariableTypeSelectable{

	private static final long serialVersionUID = -3195226739862127225L;
	
	public AbstractBooleanExpressionParser() {
		super();
	}
	
  @Override
  public List<Parser> getLazyParsers(boolean withNakedVariable) {
    
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
    
    Parsers parsers = new Parsers();
    
    parsers.add(TrueTokenParser.class);
    parsers.add(FalseTokenParser.class);
    parsers.add(InTimeRangeParser.class);
    parsers.add(BooleanSideEffectExpressionParser.class);
    parsers.add(BooleanIfExpressionParser.class);
    parsers.add(NotBooleanExpressionParser.class);
    parsers.add(new ParenthesesParser(Parser.get(BooleanClauseParser.class)));
    parsers.add(IsPresentParser.class);
    parsers.add(EqualEqualExpressionParser.class);
    parsers.add(NotEqualExpressionParser.class);
    parsers.add(GreaterOrEqualExpressionParser.class);
    parsers.add(LessOrEqualExpressionParser.class);
    parsers.add(GreaterExpressionParser.class);
    parsers.add(LessExpressionParser.class);
    parsers.add(BooleanExpressionOfStringParser.class);
    parsers.add(BooleanVariableParser.class);
    if(withNakedVariable) {
      parsers.add(NakedVariableParser.class);
    }
    return parsers;
  }
  

}