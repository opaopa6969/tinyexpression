package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.ParenthesesParser;

public abstract class AbstractBooleanFactorParser extends LazyChoice implements BooleanExpression , VariableTypeSelectable{

	private static final long serialVersionUID = -3195226739862127225L;
	
	public AbstractBooleanFactorParser() {
		super();
	}
	
  @Override
  public List<Parser> getLazyParsers(boolean withNakedVariable) {
    
    // BooleanExpression ::= 
    //    | 'true'
    //    | 'false'
    //    | 'not(' BooleanExpression ')'
    //    | '(' BooleanExpression ')'
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
    parsers.add(MethodInvocationParser.class);
    parsers.add(BooleanIfExpressionParser.class);
    parsers.add(StrictTypedBooleanMatchExpressionParser.class);
    parsers.add(NotBooleanExpressionParser.class);
    parsers.add(new ParenthesesParser(Parser.get(BooleanExpressionParser.class)));
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
      parsers.add(ExclusiveNakedVariableParser.class);
    }
    return parsers;
  }
  

}