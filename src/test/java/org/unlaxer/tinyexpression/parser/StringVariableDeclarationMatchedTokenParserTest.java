package org.unlaxer.tinyexpression.parser;

import java.util.Collection;
import java.util.Set;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.TransactionListener;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.tinyexpression.parser.javalang.StringVariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser;

public class StringVariableDeclarationMatchedTokenParserTest extends ParserTestBase{

  @Test
  public void test() {

    var parser = new StringVariableDeclarationParser();
    
    var parser2 = new StringVariableMatchedWithVariableDeclarationParser();
    
    testSucceededOnly(parser, "var $foo as string description='';", true , DoAssert.yes);
    
    Chain chain = new Chain(parser,parser2);
    
    
    testSucceededOnly(chain, "var $foo as string description='';$foo", true , DoAssert.yes);

  }

  @Override
  public Collection<TransactionListener> transactionListeners() {
    return Set.of(new VariableDeclarationParser());
  }
  
  

}
