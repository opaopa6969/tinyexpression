package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class SideEffectNameParser extends LazyChoice{

  private static final long serialVersionUID = -5885382161035099103L;
  
  public SideEffectNameParser() {
    super();
  }
  
  @Override
  public List<Parser> getLazyParsers() {
    return 
      new Parsers(
        Parser.get(SideEffectName1Parser.class),
        Parser.get(SideEffectName2Parser.class)
      );
  }
}