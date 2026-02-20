package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Name;
import org.unlaxer.RangedString;
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
  
  public static Token createToken(int position,TokenKind tokenKind) {
    
    return new Token(tokenKind, new RangedString(position, " default "), Parser.get(DefaultClauseParser.class));
  }
}