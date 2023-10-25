package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;

public class AnnotationChoice extends JavaStyleDelimitedLazyChain{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(Parser.get(AnnotationChoiceElement.class));
  }
  
}