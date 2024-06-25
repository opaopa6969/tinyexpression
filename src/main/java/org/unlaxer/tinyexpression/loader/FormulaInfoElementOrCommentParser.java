package org.unlaxer.tinyexpression.loader;

import java.util.List;
import java.util.stream.Collectors;
import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.EmptyLineParser;


public class FormulaInfoElementOrCommentParser extends LazyChoice{
  
  public static final Tag formulaInfoElementTag = new Tag("formulainfo-element");  

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(FormulaInfoElementParser.class).addTag(formulaInfoElementTag),
        Parser.get(LineCommentParser.class),//.addTag(formulaInfoElementTag),
        Parser.get(EmptyLineParser.class)//.addTag(formulaInfoElementTag)
//        Parser.get(WildCardLineParser.class)//.addTag(formulaInfoElementTag)
    );
  }
  
  public static List<Token> elements(Token ancestorOfThisParseParsed){
    List<Token> collect = ancestorOfThisParseParsed.flatten().stream()
      .filter(TokenPredicators.hasTag(formulaInfoElementTag))
      .collect(Collectors.toList());
    
    return collect;
  }
  
}