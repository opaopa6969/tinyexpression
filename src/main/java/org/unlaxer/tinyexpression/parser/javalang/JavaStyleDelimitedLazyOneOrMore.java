package org.unlaxer.tinyexpression.parser.javalang;

import java.util.function.Supplier;

import org.unlaxer.Name;
import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyOneOrMore;

public abstract class JavaStyleDelimitedLazyOneOrMore extends LazyOneOrMore {

  JavaStyleDelimitedLazyChain chain;
  
  public JavaStyleDelimitedLazyOneOrMore() {
    super();
    Parsers parser = Parsers.of(targetParser().get());
    chain = new JavaStyleDelimitedLazyChain() {

      @Override
      public Parsers getLazyParsers() {
        return parser;
      }
      
    };
  }

  public JavaStyleDelimitedLazyOneOrMore(Name name) {
    super(name);
    Parsers parser = Parsers.of(targetParser().get());
    chain = new JavaStyleDelimitedLazyChain() {

      @Override
      public Parsers getLazyParsers() {
        return parser;
      }
      
    };
  }
  
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
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