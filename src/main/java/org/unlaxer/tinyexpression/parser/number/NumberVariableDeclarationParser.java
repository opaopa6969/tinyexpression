package org.unlaxer.tinyexpression.parser.number;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.IfNotExistsParser;
import org.unlaxer.tinyexpression.parser.SetWordParser;
import org.unlaxer.tinyexpression.parser.SetterParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.bool.BooleanTypeHintParser;
import org.unlaxer.tinyexpression.parser.javalang.AbstractVariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.string.StringExpressionParser;
import org.unlaxer.tinyexpression.parser.string.StringTypeHintParser;

public class NumberVariableDeclarationParser extends AbstractVariableDeclarationParser{

  @Override
  public java.util.Optional<Parser> typeDeclaration() {
    return java.util.Optional.of(
        Parser.newInstance(NumberTypeDeclarationParser.class).addTag(typed, typeTag()));
  }

  @Override
  public Tag typeTag() {
    return Tag.of(NumberVariableDeclarationParser.class);
  }

  @Override
  public Optional<Parser> setter() {
    return Optional.of(
        new org.unlaxer.parser.combinator.Optional(
            Parser.get(NumberSetterParser.class)
        )
    );
  }

  @Override
  public Optional<ExpressionType> type() {
    return Optional.of(ExpressionType.number);
  }
  
  
  public static class TypeDefHeaderParser extends LazyChoice{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          new WordParser("typedef"),
          new WordParser("typeDefinition")
      );
    }
  }
  
  public static class TupleTypeDefinitionParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(TypeDefHeaderParser.class),
          Parser.get(IdentifierParser.class),
          new WordParser("="),
          Parser.get(TupleParser.class)
      );
    }
  }
  
  public static class TupleNameParser extends WordParser{

    public TupleNameParser() {
      super("Tuple");
    }
  }
  
  public static class TupleParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(TupleNameParser.class),
          new WordParser("<"),
          Parser.get(TypeParametersParser.class),
          new WordParser(">")
      );
    }
  }
  
  public static class TypeParametersParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(TypeParameterParser.class),
          new ZeroOrMore(
              new Chain(
                  Parser.get(CommaParser.class),
                  Parser.get(TypeParameterParser.class)
              )
          )
      );
    }
  }
  
  public static class TypeParameterParser extends LazyChoice{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(StringTypeHintParser.class),
          Parser.get(NumberTypeHintParser.class),
          Parser.get(BooleanTypeHintParser.class),
          Parser.get(TupleParser.class),
          Parser.get(MapParser.class),
          Parser.get(ListParser.class)
      );
    }
  }
  
  
  public static class MapTypeDefinitionParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(TypeDefHeaderParser.class),
          Parser.get(IdentifierParser.class),
          new WordParser("="),
          Parser.get(MapParser.class)
      );
    }
  }
  
  public static class MapNameParser extends WordParser{

    public MapNameParser() {
      super("Map");
    }
  }
  
  public static class MapParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(MapNameParser.class),
          new WordParser("<"),
          Parser.get(TypeParametersParser.class),
          new WordParser(">")
      );
    }
  }
  
  public static class ListTypeDefinitionParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(TypeDefHeaderParser.class),
          Parser.get(IdentifierParser.class),
          new WordParser("="),
          Parser.get(ListParser.class)
      );
    }
  }
  
  public static class ListNameParser extends WordParser{

    public ListNameParser() {
      super("List");
    }
  }
  
  public static class ListParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(ListNameParser.class),
          new WordParser("<"),
          Parser.get(TypeParametersParser.class),
          new WordParser(">")
      );
    }
  }
  
  public static class TupleTypeHintParser extends  TupleParser{
  }
  
  public static class MapTypeHintParser extends  MapParser{
  }
  
  public static class ListTypeHintParser extends  ListParser{
  }
  
  public static class TupleVariableDeclarationParser extends AbstractVariableDeclarationParser{

    

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
  
  
  public static class TupleSetterParser extends WhiteSpaceDelimitedLazyChain implements SetterParser{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(SetWordParser.class),
          Parser.get(()->new org.unlaxer.parser.combinator.Optional(Parser.get(IfNotExistsParser.class))),
          Parser.get(()->new Choice(
              Parser.newInstance(TupleExpressionParser.class)
            )
          )
      );
    }
    
  }
  
  public static class TupleExpressionParser extends LazyChoice{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parsers.get(ImmediatelyTupleCreationParser.class),
          Parsers.get()
      );
    }
    
  }
  
  public static class ImmediatelyTupleCreationParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          new WordParser("new"),
          new WordParser("Tuple"),
          new WordParser("("),
          new WordParser(")"),
          Parser.get(TupleCreationParser.class)
      );
    }
  }
  
  public static class TupleCreationParser extends JavaStyleDelimitedLazyChain {

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          new WordParser("["),
          Parser.get(ExpressionChoiceParser.class),
          new ZeroOrMore(
              new Chain(
                  Parser.get(CommaParser.class),
                  Parser.get(ExpressionChoiceParser.class)
              )
          ),
          new WordParser("]")
      );
    }
  }
  
  public static class ExpressionChoiceParser extends LazyChoice{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(NumberExpressionParser.class),
          Parser.get(StringExpressionParser.class),
          Parser.get(BooleanExpressionParser.class),
          Parser.get(TupleCreationParser.class),
          Parser.get(MapCreationParser.class),
          Parser.get(ListCreationParser.class)
      );
    }
  }
  
  
  public static class ListCreationParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
     return new Parsers(
         new WordParser("["),
         Parser.get(ListCreationEntryParser.class),
         new WordParser("]")

     );
    }
  }
  
  public static class ListCreationEntryParser extends LazyChoice{

    @Override
    public List<Parser> getLazyParsers() {
     return new Parsers(
         
         Parser.get(CommaSeparatedNumberExpressionsParser.class),
         Parser.get(CommaSeparatedStringExpressionsParser.class),
         Parser.get(CommaSeparatedBooleanExpressionsParser.class),
         Parser.get(CommaSeparatedTupleExpressionsParser.class),
         Parser.get(CommaSeparatedMapUmberExpressionsParser.class),
         Parser.get(CommaSeparatedListUmberExpressionsParser.class)
             );
    }
    
  }
  
  public static class MapCreationParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          new WordParser("{"),
          Parser.get(MapEntryCreationParser.class),
          new ZeroOrMore(
              new Chain(
                  new WordParser(","),
                  Parser.get(MapEntryCreationParser.class)
              )
          ),
          new WordParser("}")
      );
    }
  }
  
  public static class MapEntryCreationParser extends JavaStyleDelimitedLazyChain{

    @Override
    public List<Parser> getLazyParsers() {
      return new Parsers(
          Parser.get(ExpressionChoiceParser.class),
          new WordParser(":"),
          Parser.get(ExpressionChoiceParser.class)
      );
    }
    
  }


  
  
  
  


  
  
  
}