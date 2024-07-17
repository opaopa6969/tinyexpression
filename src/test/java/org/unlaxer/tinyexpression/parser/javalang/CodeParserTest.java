package org.unlaxer.tinyexpression.parser.javalang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.parser.elementary.StartAndEndQuotedParser.SchemeAndIdentifier;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleBuilder;

public class CodeParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    
    SimpleBuilder builder = new SimpleBuilder();
    
    builder
      .line("```java:org.unlaxer.test.Dummy")
      // ```の三文字ずつ取っているためpositionがおかしい
      .line("package org.unlaxer.test;")
      .line("public class Dummy{")
      .incTab()
      .line("public static void main(String[] args){")
      .incTab()
      .line("System.out.println(\"hello!\");")
      .decTab()
      .line("}")
      .decTab()
      .line("}")
      .line("```");
    
    String javaSource = builder.toString();
    System.out.println(javaSource);
    
    
    CodeParser codeParser = new CodeParser();
    
    TestResult testAllMatch = testAllMatch(codeParser,javaSource);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String extractContents = CodeParser.extractContents(rootToken);
    
    System.out.println("extract contents:" + extractContents);
    SchemeAndIdentifier extractSchemeAndIdentifier = CodeParser.extractSchemeAndIdentifier(rootToken);
    
    assertEquals("java", extractSchemeAndIdentifier.scheme);
    assertEquals("org.unlaxer.test.Dummy", extractSchemeAndIdentifier.idenitifier);
    assertTrue(extractContents.startsWith("package org.unlaxer.test;"));
    
  }

}
