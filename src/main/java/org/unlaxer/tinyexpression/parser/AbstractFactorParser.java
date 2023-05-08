package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.tinyexpression.parser.AbstractExpressionParser.ExpressionParser;
import org.unlaxer.tinyexpression.parser.AbstractExpressionParser.StrictTypedExpressionParser;
import org.unlaxer.tinyexpression.parser.function.CosParser;
import org.unlaxer.tinyexpression.parser.function.MaxParser;
import org.unlaxer.tinyexpression.parser.function.MinParser;
import org.unlaxer.tinyexpression.parser.function.RandomParser;
import org.unlaxer.tinyexpression.parser.function.SinParser;
import org.unlaxer.tinyexpression.parser.function.SquareRootParser;
import org.unlaxer.tinyexpression.parser.function.TanParser;

public abstract class AbstractFactorParser extends LazyChoice implements Expression  , VariableTypeSelectable{
	
	private static final long serialVersionUID = 3521391436954908685L;
	
	public AbstractFactorParser() {
		super();
	}


  @Override
  public boolean hasNakedVariableParser() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public List<Parser> getLazyParsers(boolean withNakedVariable) {
    Parsers parsers = new Parsers();
    
    parsers.add(Parser.get(SideEffectExpressionParser.class));
    parsers.add(Parser.get(IfExpressionParser.class));
    parsers.add(Parser.get(MatchExpressionParser.class));

    parsers.add(Parser.get(NumberParser.class));
    parsers.add(Parser.get(NumberVariableParser.class));
    if(withNakedVariable) {
      parsers.add(Parser.get(NakedVariableParser.class));
    }
    
    Class<? extends Parser> expresionParserClazz = withNakedVariable ? 
        ExpressionParser.class:
          StrictTypedExpressionParser.class;
    
    parsers.add(new ParenthesesParser(
        Parser.get(
            expresionParserClazz))
        
    );
    parsers.add(Parser.get(SinParser.class));
    parsers.add(Parser.get(CosParser.class));
    parsers.add(Parser.get(TanParser.class));
    parsers.add(Parser.get(SquareRootParser.class));
    parsers.add(Parser.get(MinParser.class));
    parsers.add(Parser.get(MaxParser.class));
    parsers.add(Parser.get(RandomParser.class));
    parsers.add(Parser.get(FactorOfStringParser.class));
    parsers.add(Parser.get(ToNumParser.class));
    return parsers;
    
  }
  
  
  public static class FactorParser extends AbstractFactorParser{

    @Override
    public List<Parser> getLazyParsers() {
      return getLazyParsers(true);
    }
    
    @Override
    public boolean hasNakedVariableParser() {
      return true;
    }
  }
  
  public static class StrictTypedFactorParser extends AbstractFactorParser{

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
