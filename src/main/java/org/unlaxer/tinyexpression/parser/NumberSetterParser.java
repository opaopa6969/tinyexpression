package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class NumberSetterParser extends WhiteSpaceDelimitedLazyChain/*JavaStyleDelimitedLazyChain*/
  implements NumberExpression , SetterParser{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(SetWordParser.class),
        Parser.get(()->new Optional(Parser.get(IfNotExistsParser.class))),
        Parser.get(()->new Choice(
            Parser.newInstance(StrictTypedNumberExpressionParser.class),
            Parser.get(NumberExpressionParser.class)
          )
        )
    );
  }
  
  
  public static class Tuple{
    
    public static class TupleElement{
      
      ExpressionType expressionType;
      
    }
  }
}