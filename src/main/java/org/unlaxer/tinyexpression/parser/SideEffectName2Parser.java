package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;

public class SideEffectName2Parser extends WordParser{

  private static final long serialVersionUID = -5885382161035099103L;
  
  List<Parser> parsers;


  public SideEffectName2Parser() {
    super("external");
  }
}