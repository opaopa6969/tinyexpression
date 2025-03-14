package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpression;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpressionOfStringParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanIfExpressionParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanSideEffectExpressionParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanVariableParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberEqualEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberGreaterExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberGreaterOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberLessExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberLessOrEqualExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberNotEqualExpressionParser;

public abstract class AbstractBooleanFactorParser extends LazyChoice implements BooleanExpression , VariableTypeSelectable{

	private static final long serialVersionUID = -3195226739862127225L;
	
	public AbstractBooleanFactorParser() {
		super();
	}
	
  @Override
  public org.unlaxer.parser.Parsers getLazyParsers(boolean withNakedVariable) {
    
    // BooleanExpression ::= 
    //    | 'true'
    //    | 'false'
    //    | 'not(' BooleanExpression ')'
    //    | '(' BooleanExpression ')'
    //    | BooleanExpressionOfString
    //    | Expression '==' Expression 
    //    | Expression '!=' Expression 
    //    | Expression '>=' Expression 
    //    | Expression '<=' Expression 
    //    | Expression '>' Expression 
    //    | Expression '<' Expression 
    //    | Variable
    
    Parsers parsers = new Parsers();
    
    parsers.add(TrueTokenParser.class);
    parsers.add(FalseTokenParser.class);
    parsers.add(InDayTimeRangeParser.class);
    parsers.add(InTimeRangeParser.class);
    parsers.add(BooleanSideEffectExpressionParser.class);
    parsers.add(BooleanIfExpressionParser.class);
    parsers.add(StrictTypedBooleanMatchExpressionParser.class);
    parsers.add(NotBooleanExpressionParser.class);
    parsers.add(new ParenthesesParser(Parser.get(BooleanExpressionParser.class)));
    parsers.add(IsPresentParser.class);
    parsers.add(BooleanExpressionOfStringParser.class); // <- number == number系より早く評価しないとダメ
    parsers.add(NumberEqualEqualExpressionParser.class);
    parsers.add(NumberNotEqualExpressionParser.class);
    parsers.add(NumberGreaterOrEqualExpressionParser.class);
    parsers.add(NumberLessOrEqualExpressionParser.class);
    parsers.add(NumberGreaterExpressionParser.class);
    parsers.add(NumberLessExpressionParser.class);
//    parsers.add(BooleanExpressionOfStringParser.class);　　// <- number == number系より早く評価しないとダメ
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