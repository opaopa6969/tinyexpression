package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;

import org.unlaxer.Name;
import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.SemiColonParser;
import org.unlaxer.tinyexpression.parser.DescriptionParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.SetterParser;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public abstract class AbstractVariableDeclarationParser extends JavaStyleDelimitedLazyChain{
  
  public final static Tag typed = Tag.of("typed");
  
  @Override
  public List<Parser> getLazyParsers() {
    
    Parsers parsers = new Parsers();
    parsers.add(
        new Choice(
            new WordParser("variable"),
            new WordParser("var")
        )
    );
    parsers.add(Parser.get(NakedVariableParser.class));
    typeDeclaration().ifPresent(parsers::add);
    setter().ifPresent(parsers::add);
//    parsers.add(new Optional(Parser.get(SetterParser.class)));
    parsers.add(Parser.get(DescriptionParser.class));
    parsers.add(Parser.get(SemiColonParser.class));
    
    return parsers;
  }
  
  public abstract java.util.Optional<Parser> setter();
  public abstract java.util.Optional<Parser> typeDeclaration();
  public abstract Tag typeTag();
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static Token extractVariable(Token thisParserParsed){

    @SuppressWarnings("unchecked")
    Token newCreatesOf = thisParserParsed.newCreatesOf(
      TokenPredicators.parsers(NakedVariableParser.class),
      TokenPredicators.hasTag(typed),
      TokenPredicators.parsers(SetterParser.class),
      TokenPredicators.parsers(DescriptionParser.class)
    );
    return newCreatesOf; 
        
  }

  static class NoMatchParser extends WordParser{

    public NoMatchParser(Name name, String word) {
      super(name, word);
    }
  }
}