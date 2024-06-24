package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Not;
import org.unlaxer.tinyexpression.parser.javalang.BooleanTypeDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.NumberTypeDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.StringTypeDeclarationParser;
import org.unlaxer.util.cache.SupplierBoundCache;

@SuppressWarnings("serial")
public class ExclusiveNakedVariableParser extends NakedVariableParser {//implements Expression , BooleanExpression , StringExpression{
  
  static final SupplierBoundCache<ExclusiveNakedVariableParser> SINGLETON = new SupplierBoundCache<>(ExclusiveNakedVariableParser::new);

  public ExclusiveNakedVariableParser() {
    super();
  }

  public ExclusiveNakedVariableParser(Name name) {
    super(name);
  }
  
  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    
    return 
      new Parsers(
        Parser.get(DollarParser.class),
        Parser.newInstance(IdentifierParser.class).addTag(VariableParser.variableNameTag),
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
  
  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.empty();
  }
  
  public static ExclusiveNakedVariableParser get() {
    
    return SINGLETON.get();
  }
}