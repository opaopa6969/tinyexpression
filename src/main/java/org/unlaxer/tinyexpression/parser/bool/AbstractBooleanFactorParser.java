package org.unlaxer.tinyexpression.parser.bool;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.tinyexpression.parser.ExclusiveNakedVariableParser;
import org.unlaxer.tinyexpression.parser.FalseTokenParser;
import org.unlaxer.tinyexpression.parser.InTimeRangeParser;
import org.unlaxer.tinyexpression.parser.IsPresentParser;
import org.unlaxer.tinyexpression.parser.MethodInvocationParser;
import org.unlaxer.tinyexpression.parser.NotBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.TrueTokenParser;
import org.unlaxer.tinyexpression.parser.VariableTypeSelectable;
import org.unlaxer.tinyexpression.parser.number.NumberEqualEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.number.NumberGreaterExpressionParser;
import org.unlaxer.tinyexpression.parser.number.NumberGreaterOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.number.NumberLessExpressionParser;
import org.unlaxer.tinyexpression.parser.number.NumberLessOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.number.NumberNotEqualExpressionParser;

public abstract class AbstractBooleanFactorParser extends LazyChoice implements BooleanExpression , VariableTypeSelectable{

	private static final long serialVersionUID = -3195226739862127225L;
	
	public AbstractBooleanFactorParser() {
		super();
	}
	
  @Override
  public Parsers getLazyParsers(boolean withNakedVariable) {
    
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
    parsers.add(BooleanIfExpressionParser.class);
    parsers.add(StrictTypedBooleanMatchExpressionParser.class);
    parsers.add(NotBooleanExpressionParser.class);
    parsers.add(new ParenthesesParser(Parser.get(BooleanExpressionParser.class)));
    parsers.add(IsPresentParser.class);
    parsers.add(NumberEqualEqualExpressionParser.class);
    parsers.add(NumberNotEqualExpressionParser.class);
    parsers.add(NumberGreaterOrEqualExpressionParser.class);
    parsers.add(NumberLessOrEqualExpressionParser.class);
    parsers.add(NumberGreaterExpressionParser.class);
    parsers.add(NumberLessExpressionParser.class);
    parsers.add(BooleanExpressionOfStringParser.class);
    parsers.add(BooleanVariableParser.class);
    if(withNakedVariable) {
      parsers.add(ExclusiveNakedVariableParser.class);
    }
    if(MethodInvocationParser.enabled1) {
      parsers.add(MethodInvocationParser.class);
    }
    return parsers;
  }
  

}