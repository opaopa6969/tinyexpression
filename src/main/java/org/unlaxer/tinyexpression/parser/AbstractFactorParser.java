package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.tinyexpression.parser.function.CosParser;
import org.unlaxer.tinyexpression.parser.function.MaxParser;
import org.unlaxer.tinyexpression.parser.function.MinParser;
import org.unlaxer.tinyexpression.parser.function.RandomParser;
import org.unlaxer.tinyexpression.parser.function.SinParser;
import org.unlaxer.tinyexpression.parser.function.SquareRootParser;
import org.unlaxer.tinyexpression.parser.function.TanParser;

public abstract class AbstractFactorParser extends LazyChoice implements NumberExpression  , VariableTypeSelectable{
	
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
  public org.unlaxer.parser.Parsers getLazyParsers(boolean withNakedVariable) {
    Parsers parsers = new Parsers();
    
    parsers.add(NumberSideEffectExpressionParser.class);
    parsers.add(NumberIfExpressionParser.class);
    parsers.add(StrictTypedNumberMatchExpressionParser.class);

    parsers.add(NumberParser.class);
    parsers.add(NumberVariableParser.class);
    if(withNakedVariable) {
      parsers.add(ExclusiveNakedVariableParser.class);
    }
    
    Class<? extends Parser> expresionParserClazz = withNakedVariable ? 
        NumberExpressionParser.class:
          StrictTypedNumberExpressionParser.class;
    
    parsers.add(new ParenthesesParser(
        Parser.newInstance(
            expresionParserClazz)
        )
    );
    parsers.add(SinParser.class);
    parsers.add(CosParser.class);
    parsers.add(TanParser.class);
    parsers.add(SquareRootParser.class);
    parsers.add(MinParser.class);
    parsers.add(MaxParser.class);
    parsers.add(RandomParser.class);
    parsers.add(FactorOfStringParser.class);
    parsers.add(ToNumParser.class);
    if(MethodInvocationParser.enabled2) {
      parsers.add(MethodInvocationParser.class);
    }
    return parsers;
    
  }

}
