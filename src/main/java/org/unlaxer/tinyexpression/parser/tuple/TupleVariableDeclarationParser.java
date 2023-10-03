package org.unlaxer.tinyexpression.parser.tuple;

import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.javalang.AbstractVariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.number.NumberVariableDeclarationParser.TupleSetterParser;

public class TupleVariableDeclarationParser extends AbstractVariableDeclarationParser{

    

    @Override
    public Optional<ExpressionType> type() {
      return Optional.of(ExpressionType.tuple);
    }
  
    @Override
    public Optional<Parser> setter() {
      return Optional.of(
          new org.unlaxer.parser.combinator.Optional(
              Parser.get(TupleSetterParser.class)
          )
      );
    }
  
    @Override
    public Optional<Parser> typeDeclaration() {
      return java.util.Optional.of(
          Parser.newInstance(TupleTypeDeclarationParser.class).addTag(typed, typeTag()));
    }
  
    @Override
    public Tag typeTag() {
      return Tag.of(TupleVariableDeclarationParser.class);
    }
  
  }