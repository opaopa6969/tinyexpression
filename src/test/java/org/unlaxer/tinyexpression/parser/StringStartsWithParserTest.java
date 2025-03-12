package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class StringStartsWithParserTest extends ParserTestBase {

  public static final StringStartsWithParser test = new StringStartsWithParser();

  @Test
  public void test(){
    setLevel(OutputLevel.mostDetail);

    testAllMatch(test,"'a'.startsWith('a','b')");
//    ParseContext parseContext = new ParseContext(new StringSource("'a'.startsWith('a','b')"));
//    Parsed parse = test.parse(parseContext);
//    System.out.println(parse.status);
//    System.out.println(parse.getMessage());

  }



}