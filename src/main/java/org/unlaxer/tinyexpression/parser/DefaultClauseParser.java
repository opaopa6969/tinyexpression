package org.unlaxer.tinyexpression.parser;

import org.unlaxer.CodePointOffset;
import org.unlaxer.Name;
import org.unlaxer.Source;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;

public class DefaultClauseParser extends WordParser{
  
  final static String word="default";

  public DefaultClauseParser(Name name) {
    super(name, word);
  }
  public DefaultClauseParser() {
    super(word);
  }
  
  public static Token createToken(Source rootSource , CodePointOffset position,TokenKind tokenKind) {
    
    return new Token(tokenKind,StringSource.createSubSource(" default " , rootSource , position), Parser.get(DefaultClauseParser.class));
  }
}