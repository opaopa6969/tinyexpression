package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.RootParserIndicator;
import org.unlaxer.tinyexpression.parser.javalang.AnnotationsParser;
import org.unlaxer.tinyexpression.parser.javalang.ImportsParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationsParser;

public class TinyExpressionParser extends JavaStyleDelimitedLazyChain implements RootParserIndicator{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(ImportsParser.class),
        Parser.get(VariableDeclarationsParser.class),
        Parser.get(AnnotationsParser.class),
        Parser.get(NumberExpressionParser.class)
    );
  }
  
}