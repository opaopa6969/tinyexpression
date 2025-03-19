package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.function.CosParser;
import org.unlaxer.tinyexpression.parser.function.MaxParser;
import org.unlaxer.tinyexpression.parser.function.MinParser;
import org.unlaxer.tinyexpression.parser.function.RandomParser;
import org.unlaxer.tinyexpression.parser.function.SinParser;
import org.unlaxer.tinyexpression.parser.function.SquareRootParser;
import org.unlaxer.tinyexpression.parser.function.TanParser;
import org.unlaxer.tinyexpression.parser.numbertype.BigDecimalPrefixNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.BigIntegerPrefixNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.BytePrefixNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.DoublePrefixNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.DoubleSuffixNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.FloatPrefixNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.FloatSuffixNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.IntPrefixNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.LongPrefixNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.LongSuffixNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.NoSuffixNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpression;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberIfExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberSideEffectExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberVariableParser;
import org.unlaxer.tinyexpression.parser.numbertype.ShortPrefixNumberParser;
import org.unlaxer.tinyexpression.parser.numbertype.StrictTypedNumberExpressionParser;

public abstract class AbstractNumberFactorParser extends LazyChoice implements NumberExpression  , VariableTypeSelectable{

	private static final long serialVersionUID = 3521391436954908685L;

	SpecifiedExpressionTypes specifiedExpressionType;

	public AbstractNumberFactorParser(SpecifiedExpressionTypes specifiedExpressionType) {
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

    //parsers.add(NumberParser.class);
    parsers.add(BytePrefixNumberParser.class);
    parsers.add(ShortPrefixNumberParser.class);
    parsers.add(IntPrefixNumberParser.class);
    parsers.add(LongPrefixNumberParser.class);
    parsers.add(LongSuffixNumberParser.class);
    parsers.add(BigIntegerPrefixNumberParser.class);
    parsers.add(FloatPrefixNumberParser.class);
    parsers.add(FloatSuffixNumberParser.class);
    parsers.add(DoublePrefixNumberParser.class);
    parsers.add(DoubleSuffixNumberParser.class);
    parsers.add(BigDecimalPrefixNumberParser.class);
    parsers.add(new NoSuffixNumberParser(specifiedExpressionType));



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
