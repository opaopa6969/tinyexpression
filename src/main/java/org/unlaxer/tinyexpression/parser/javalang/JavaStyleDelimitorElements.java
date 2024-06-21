package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.BlockComment;
import org.unlaxer.parser.clang.CPPComment;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.SpaceDelimitor;

public class JavaStyleDelimitorElements extends LazyChoice{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    
    return new Parsers(
        Parser.get(BlockComment.class),
        Parser.get(CPPComment.class),
        Parser.get(SpaceDelimitor.class)
    );
  }
  
}
