package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.EqualParser;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.LazyZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.StringExpressionParser;

public class AnnotaionParser extends LazyChain{
  
  @Override
  public List<Parser> getLazyParsers() {
    
    return new Parsers(
        new WordParser("@"),
        Parser.get(IdentifierParser.class),
        // ここをoptionalにすると、数式の前にパラメータなしのannoataionをつけたときに数式なのか、annotation内の数式なのかを区別がつかない
//        new org.unlaxer.parser.combinator.Optional(Parser.get(AnnotationParametersParser.class))
        Parser.get(AnnotationParametersParser.class)
    );
  }
  
  public static class AnnotationParametersParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(LeftParenthesisParser.class),
          Parser.get(AnnotationFirstParameterParser.class),
          Parser.get(AnnotationParametersSuccessorElementParser.class),
          Parser.get(RightParenthesisParser.class)
      );
    }
  }
  
  public static class AnnotationFirstParameterParser extends LazyZeroOrMore{

    @Override
    public Supplier<Parser> getLazyParser() {
      return ()->
          Parser.get(AnnotationParameterParser.class);
    }

    @Override
    public Optional<Parser> getLazyTerminatorParser() {
      return Optional.empty();
    }
    
  }
  
  public static class AnnotationParametersSuccessorElementParser extends LazyZeroOrMore{

    @Override
    public Supplier<Parser> getLazyParser() {
      return ()->
          Parser.get(AnnotationParametersSuccessorElementChainParser.class);
    }

    @Override
    public Optional<Parser> getLazyTerminatorParser() {
      return Optional.empty();
    }
    
  }
  
  public static class AnnotationParametersSuccessorElementChainParser extends LazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(CommaParser.class),
          Parser.get(AnnotationParameterParser.class)
      );
    }

  }

  
  
  public static class AnnotationParameterParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(IdentifierParser.class),
          Parser.get(EqualParser.class),
          new Choice(
            Parser.get(StringExpressionParser.class),
            Parser.get(BooleanExpressionParser.class),
            Parser.get(NumberExpressionParser.class)
          )
      );
    }
  }
}
