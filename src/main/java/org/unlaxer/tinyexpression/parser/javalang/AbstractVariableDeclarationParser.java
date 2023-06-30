package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;

import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Optional;
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
    parsers.add(new Optional(Parser.get(SetterParser.class)));
    parsers.add(Parser.get(DescriptionParser.class));
    parsers.add(Parser.get(SemiColonParser.class));
    
    return parsers;
   }
  
  public abstract java.util.Optional<Parser> typeDeclaration();
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static Token extractVariable(Token thisParserParsed){
    
    return thisParserParsed.newCreatesOf(
      TokenPredicators.parsers(
          NakedVariableParser.class,
          TypeDeclarationParser.class,
          SetterParser.class,
          DescriptionParser.class
      )
    );
  }

}