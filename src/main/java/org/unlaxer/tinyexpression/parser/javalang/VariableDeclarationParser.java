package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.SemiColonParser;
import org.unlaxer.tinyexpression.parser.DescriptionParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.SetterParser;

public class VariableDeclarationParser extends WhiteSpaceDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        new WordParser("variable"),
        Parser.get(NakedVariableParser.class),
        Parser.get(TypeDeclarationParser.class),
        Parser.get(SetterParser.class),
        Parser.get(DescriptionParser.class),
        Parser.get(SemiColonParser.class)
    );
  }
}