package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyZeroOrMore;

public abstract class JavaStyleDelimitedLazyZeroOrMore extends LazyZeroOrMore {

  JavaStyleDelimitedLazyChain chain;
  
  public JavaStyleDelimitedLazyZeroOrMore() {
    super();
    List<Parser> parser = List.of(getLazyParser().get());
    chain = new JavaStyleDelimitedLazyChain() {

      @Override
      public List<Parser> getLazyParsers() {
        return parser;
      }
      
    };
  }

  public JavaStyleDelimitedLazyZeroOrMore(Name name) {
    super(name);
    List<Parser> parser = List.of(getLazyParser().get());
    chain = new JavaStyleDelimitedLazyChain() {

      @Override
      public List<Parser> getLazyParsers() {
        return parser;
      }
      
    };
  }
}