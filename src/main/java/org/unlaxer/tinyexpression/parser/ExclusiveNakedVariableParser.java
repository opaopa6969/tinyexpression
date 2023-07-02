package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.Not;
import org.unlaxer.tinyexpression.parser.javalang.BooleanTypeDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.NumberTypeDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.StringTypeDeclarationParser;

public class ExclusiveNakedVariableParser extends LazyChain implements VariableParser{//implements Expression , BooleanExpression , StringExpression{

    public ExclusiveNakedVariableParser() {
      super();
    }

    public ExclusiveNakedVariableParser(Name name) {
      super(name);
    }
    
    @Override
    public List<Parser> getLazyParsers() {
      
      return 
        new Parsers(
          Parser.get(DollarParser.class),
          Parser.get(IdentifierParser.class),
          new Not(
//              new MatchOnly(
                  new Choice(
                      Parser.get(NumberTypeDeclarationParser.class),
                      Parser.get(StringTypeDeclarationParser.class),
                      Parser.get(BooleanTypeDeclarationParser.class)
                  )
//              )
          )
        );
    }
    
    public static String getVariableName(Token thisParserParsed) {
      String variableName = thisParserParsed.tokenString.get().substring(1);
      return variableName; 
    }

    @Override
    public Optional<VariableType> type() {
      return Optional.empty();
    }

  }