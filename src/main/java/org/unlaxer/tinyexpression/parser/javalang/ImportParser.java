package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.Parsed;
import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.TokenPredicators;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.SemiColonParser;
import org.unlaxer.tinyexpression.parser.JavaClassMethodParser;
import org.unlaxer.tinyexpression.parser.JavaClassNameParser;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public class ImportParser extends JavaStyleDelimitedLazyChain{
  
  static Tag javaClassMethodOrClassNameTag = Tag.of("javaClassMethodOrClassName");

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        new WordParser("import"),
        new Choice(
            Parser.get(JavaClassMethodParser.class).addTag(javaClassMethodOrClassNameTag),
            Parser.get(JavaClassNameParser.class).addTag(javaClassMethodOrClassNameTag)
        ),//.addTag(choiceTag),
        new WordParser("as"),
        Parser.get(IdentifierParser.class),
        Parser.get(SemiColonParser.class)
    );
  }
  
  @TokenExtractor
  public static Token extractImport(Token thisParserParsed){
    
    //choiceを選択するにはこの方法が良いがTiming.UseOperatorOperandTreeの時に面倒
//    Token choice = thisParserParsed.getChild(
//        TokenPredicators.hasTag(choiceTag),
//        // Choice等はoriginalにしかか含まれない
//        ChildrenKind.original);
//    Token javaClassMethodOrClassName = ChoiceInterface.choiced(choice);
    Token javaClassMethodOrClassName = thisParserParsed.getChild(
        TokenPredicators.hasTag(javaClassMethodOrClassNameTag));
    Token identifier = thisParserParsed.getChildWithParser(IdentifierParser.class);
    
    return thisParserParsed.newCreatesOf(javaClassMethodOrClassName,identifier);
  }
  
  @TokenExtractor(timings = Timing.UseOperatorOperandTree)
  public static Token extractJavaClassMethodOrClassName(Token thisParserParsed){
    return thisParserParsed.getChildFromAstNodes(0);
  }

  @TokenExtractor(timings = Timing.UseOperatorOperandTree)
  public static Token extractIdentifier(Token thisParserParsed){
    return thisParserParsed.getChildFromAstNodes(1);
  }

}