package org.unlaxer.tinyexpression.parser;

import java.util.Collection;
import java.util.Set;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.listener.TransactionListener;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.tinyexpression.parser.javalang.StringVariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser;

public class StringVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    StringVariableParser parser = new StringVariableParser();
    
    testUnMatch(parser, "$foo");
    testAllMatch(parser, "$foo as string");
    testAllMatch(parser, "$foo as String");
    testAllMatch(parser, "(string)$foo");
    testAllMatch(parser, "(String)$foo");
    testUnMatch(parser, "$foo as boolean");
    testUnMatch(parser, "(boolean)$foo");
  }
  
  
  @Test
  public void testMatchesWithDeclaration() {
    
    setLevel(OutputLevel.detail);
    StringVariableDeclarationParser declarationParser = new StringVariableDeclarationParser();
    
    {
      StringVariableParser parser = new StringVariableParser();
      
      Chain chain = new Chain(declarationParser,parser);
      
      testAllMatch(chain, "var $foo as string description='';$foo");
    }
    
    testAllMatch(declarationParser, "var $foo as string description='';");
    {
      NakedVariableParser parser = new NakedVariableParser();
      
      Chain chain = new Chain(declarationParser,parser);
      
      testAllMatch(chain, "var $foo as string description='';$foo");
      
    }
    
  }


  @Override
  public Collection<TransactionListener> transactionListeners() {
    return Set.of(new VariableDeclarationParser());
  }
}
