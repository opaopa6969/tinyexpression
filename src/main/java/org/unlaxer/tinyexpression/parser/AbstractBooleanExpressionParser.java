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
    
    parsers.add(Parser.get(TrueTokenParser.class));
    parsers.add(Parser.get(FalseTokenParser.class));
    parsers.add(Parser.get(InTimeRangeParser.class));
    parsers.add(Parser.get(SideEffectBooleanExpressionParser.class));
    parsers.add(Parser.get(SideEffectStringToBooleanExpressionParser.class));
    parsers.add(Parser.get(NotBooleanExpressionParser.class));
    parsers.add(new ParenthesesParser(Parser.get(BooleanClauseParser.class)));
    parsers.add(Parser.get(IsPresentParser.class));
    parsers.add(Parser.get(EqualEqualExpressionParser.class));
    parsers.add(Parser.get(NotEqualExpressionParser.class));
    parsers.add(Parser.get(GreaterOrEqualExpressionParser.class));
    parsers.add(Parser.get(LessOrEqualExpressionParser.class));
    parsers.add(Parser.get(GreaterExpressionParser.class));
    parsers.add(Parser.get(LessExpressionParser.class));
    parsers.add(Parser.get(BooleanExpressionOfStringParser.class));
    parsers.add(Parser.get(BooleanVariableParser.class));
    if(withNakedVariable) {
      parsers.add(Parser.get(NakedVariableParser.class));
    }
    return parsers;
  }
  
  
  public static class BooleanExpressionParser extends AbstractBooleanExpressionParser{

     @Override
    public List<Parser> getLazyParsers() {
      return getLazyParsers(true);
    }

    @Override
    public boolean hasNakedVariableParser() {
      return true;
    }
  }
  
  public static class StrictTypedBooleanExpressionParser extends AbstractBooleanExpressionParser{

    @Override
   public List<Parser> getLazyParsers() {
     return getLazyParsers(false);
   }

   @Override
   public boolean hasNakedVariableParser() {
     return false;
   }
 }
  

}