package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Name;
import org.unlaxer.parser.elementary.WordParser;

public class DefaultClauseParser extends WordParser{
  
  final static String word="default";

  public DefaultClauseParser(Name name) {
    super(name, word);
  }
  public DefaultClauseParser() {
    super(word);
  }
}