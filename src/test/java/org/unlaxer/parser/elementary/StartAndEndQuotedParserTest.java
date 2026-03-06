package org.unlaxer.parser.elementary;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.NotPropagatableSource;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.tinyexpression.parser.javalang.CodeEndParser;

public class StartAndEndQuotedParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    
    CodeEndParser codeEndParser = Parser.get(CodeEndParser.class);
    
    testAllMatch(codeEndParser, "```");
    testAllMatch(codeEndParser, "```\n");
    
    ZeroOrMore zeroOrMore = new ZeroOrMore(
        new Choice(
          new EscapeInQuotedParser(),
          new NotPropagatableSource(codeEndParser)
        )
      );

    testAllMatch(zeroOrMore, "");
    testAllMatch(zeroOrMore, "\\g");
//    testAllMatch(zeroOrMore, "A");
  }

}
