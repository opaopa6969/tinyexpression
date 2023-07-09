package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;
import java.util.function.Supplier;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyOneOrMore;

public abstract class JavaStyleDelimitedLazyOneOrMore extends LazyOneOrMore {

  JavaStyleDelimitedLazyChain chain;
  
  public JavaStyleDelimitedLazyOneOrMore() {
    super();
    List<Parser> parser = List.of(targetParser().get());
    chain = new JavaStyleDelimitedLazyChain() {

      @Override
      public List<Parser> getLazyParsers() {
        return parser;
      }
      
    };
  }

  public JavaStyleDelimitedLazyOneOrMore(Name name) {
    super(name);
    List<Parser> parser = List.of(targetParser().get());
    chain = new JavaStyleDelimitedLazyChain() {

      @Override
      public List<Parser> getLazyParsers() {
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