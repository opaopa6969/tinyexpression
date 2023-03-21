package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;

public class AsParser extends WordParser{

  private static final long serialVersionUID = -583418216997099103L;
  
  List<Parser> parsers;


  public AsParser() {
    super("as");
  }
}