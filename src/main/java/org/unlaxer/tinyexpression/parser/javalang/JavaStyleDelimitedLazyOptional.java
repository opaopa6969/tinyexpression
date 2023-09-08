package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;
import java.util.function.Supplier;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyOptional;

public abstract class JavaStyleDelimitedLazyOptional extends LazyOptional {

  JavaStyleDelimitedLazyChain chain;
  
  public JavaStyleDelimitedLazyOptional() {
    super();
    List<Parser> parser = List.of(targetParser().get());
    chain = new JavaStyleDelimitedLazyChain() {

      @Override
      public List<Parser> getLazyParsers() {
        return parser;
      }
      
    };
  }

  public JavaStyleDelimitedLazyOptional(Name name) {
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