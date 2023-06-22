package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;

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

public class VariableDeclarationParser extends JavaStyleDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        new Choice(
            new WordParser("variable"),
            new WordParser("var")
        ),
        Parser.get(NakedVariableParser.class),
        Parser.get(TypeDeclarationParser.class),
        Parser.get(SetterParser.class),
        Parser.get(DescriptionParser.class),
        Parser.get(SemiColonParser.class)
    );
  }
  
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