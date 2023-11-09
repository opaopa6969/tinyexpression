package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.CodePointOffset;
import org.unlaxer.Source;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;

public class AsParser extends WordParser{

  private static final long serialVersionUID = -583418216997099103L;
  
  List<Parser> parsers;


  public AsParser() {
    super("as");
  }
  
  public static Token createToken(Source rootSource, CodePointOffset position,TokenKind tokenKind) {
    
    return new Token(tokenKind, StringSource.createSubSource(" as ", rootSource ,  position) , Parser.get(AsParser.class));
  }
}