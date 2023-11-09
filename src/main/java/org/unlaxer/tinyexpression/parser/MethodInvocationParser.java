package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class MethodInvocationParser extends JavaStyleDelimitedLazyChain{

  public static boolean enabled1 = true;
  public static boolean enabled2 = true;
  public static boolean enabled3 = true;
 
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        //ここをoptionalにするとparseが意図しない結果となる。
//        new Optional(
            Parser.get(MethodInvocationHeaderParser.class),
//        ),
        Parser.get(IdentifierParser.class),
        Parser.get(LeftParenthesisParser.class),
        new Optional(
            Parser.get(ArgumentsParser.class)
        ),
        Parser.get(RightParenthesisParser.class)
    );
  }
  
  @TokenExtractor
  public static java.util.Optional<Token> getParametersClause(Token thisParserParsed) {
    
    Parser.checkTokenParsedBySpecifiedParser(thisParserParsed, MethodInvocationParser.class);
    
    return thisParserParsed.getChildWithParserAsOptional(ArgumentsParser.class); //4
  }
  
  @TokenExtractor
  public static Token getMethodName(Token thisParserParsed) {
    
    Parser.checkTokenParsedBySpecifiedParser(thisParserParsed, MethodInvocationParser.class);
    
    return thisParserParsed.getChildWithParser(IdentifierParser.class); //4
  }
  
  public static String getMethodNameAsString(Token thisParserParsed) {
    
    return getMethodName(thisParserParsed).getSource().sourceAsString();
  }
}