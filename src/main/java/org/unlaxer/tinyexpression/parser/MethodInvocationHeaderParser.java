package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class MethodInvocationHeaderParser extends LazyChoice{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(MethodInvocationHeader1Parser.class),
        Parser.get(()->new WordParser("call")),
        Parser.get(()->new WordParser("internal"))
    );
  }
  
  public static class MethodInvocationHeader1Parser extends JavaStyleDelimitedLazyChain{
    
    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          Parser.get(()->new WordParser("call")),
          Parser.get(()->new WordParser("internal"))
      );
    }
  }
  
}