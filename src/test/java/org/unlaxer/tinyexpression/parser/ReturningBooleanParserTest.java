package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.listener.OutputLevel;

public class ReturningBooleanParserTest extends ParserTestBase{


    @Test
    public void test() {
      setLevel(OutputLevel.detail);
      ReturningBooleanParser returningStringParser = new ReturningBooleanParser();
      
      testAllMatch(returningStringParser, "returning as boolean default true");
      testAllMatch(returningStringParser, "returning as boolean default 1==0");
      TestResult testAllMatch = testAllMatch(returningStringParser, "returning as boolean default (1==0)");
      Token rootToken = testAllMatch.parsed.getRootToken();
      
      TokenPrinter.output(rootToken, System.out);
    }
}
