package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.List;

import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.TypeHint;
import org.unlaxer.tinyexpression.parser.TypeHintVariableParser;
import org.unlaxer.tinyexpression.parser.ReturningParser.Returning;

public abstract class NumberClassParser extends LazyChoice implements TypeHint , Returning ,TypeHintVariableParser{

  public abstract List<NumberClassName> numberClassNames();
  @Override
  public Parsers getLazyParsers() {

    Parsers parsers = new Parsers();

    numberClassNames().stream()
      .map(numberClassName->new NumberWordParser(numberClassName.word , numberClassName.expressionType()))
      .forEach(parsers::add);
    return parsers;
  }


  public static class NumberWordParser extends WordParser implements TypeHint{

    ExpressionType expressionType;


    public NumberWordParser(String word , ExpressionType expressionType) {
      super(word);
      this.expressionType = expressionType;
    }

    public ExpressionType type() {
      return expressionType;
    }

  }


}