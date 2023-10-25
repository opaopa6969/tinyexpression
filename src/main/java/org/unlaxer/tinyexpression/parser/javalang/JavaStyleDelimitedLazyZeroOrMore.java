package org.unlaxer.tinyexpression.parser.javalang;

import java.util.function.Supplier;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyZeroOrMore;

public abstract class JavaStyleDelimitedLazyZeroOrMore extends LazyZeroOrMore {

  JavaStyleDelimitedLazyChain chain;
  
  public JavaStyleDelimitedLazyZeroOrMore() {
    super();
    Parsers parser = Parsers.of(targetParser().get());
    chain = new JavaStyleDelimitedLazyChain() {

      @Override
      public org.unlaxer.parser.Parsers getLazyParsers() {
        return parser;
      }
      
    };
  }

  public JavaStyleDelimitedLazyZeroOrMore(Name name) {
    super(name);
    Parsers parser = Parsers.of(targetParser().get());
    chain = new JavaStyleDelimitedLazyChain() {

      @Override
      public org.unlaxer.parser.Parsers getLazyParsers() {
        return parser;
      }
      
    };
  }
  

  @Override
  public Supplier<Parser> getLazyParser() {
    return ()->chain;
  }

  @Override
  public java.util.Optional<Parser> getLazyTerminatorParser() {
    return java.util.Optional.empty();
  }
  
  public abstract Supplier<Parser> targetParser();
}
  