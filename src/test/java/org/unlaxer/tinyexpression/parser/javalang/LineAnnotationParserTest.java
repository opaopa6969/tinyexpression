package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.elementary.WordParser;

public class LineAnnotationParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    
    LineAnnotationParser lineAnnotationParser = new LineAnnotationParser();
    
    testAllMatch(lineAnnotationParser, "@test niku");
    testAllMatch(lineAnnotationParser, "@test");
    
    
    WordParser mae = new WordParser("前文");
    WordParser ato = new WordParser("\n後文");
    
    Chain chain = new Chain(mae,lineAnnotationParser,ato);
    
    
    testAllMatch(chain,"前文@test niku\n後文");
    
  }

}
