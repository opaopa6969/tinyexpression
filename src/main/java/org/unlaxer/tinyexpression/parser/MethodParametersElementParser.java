package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class MethodParametersElementParser extends LazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(MethodParameterParser.class),
        new ZeroOrMore(
            new JavaStyleDelimitedLazyChain() {

              @Override
              public List<Parser> getLazyParsers() {
                return new Parsers(
                    Parser.get(CommaParser.class),
                    Parser.get(MethodParameterParser.class)
                );
              }
            }
        )
    );
  }
  
  @TokenExtractor
  public List<TypedToken<ExpressionType>> typedVariableParsers(TypedToken<MethodParametersElementParser> thisParserParsed){
   
    ここで
  }
}