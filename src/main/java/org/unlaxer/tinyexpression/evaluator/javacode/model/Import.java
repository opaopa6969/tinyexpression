package org.unlaxer.tinyexpression.evaluator.javacode.model;

import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.tinyexpression.parser.javalang.ImportParser;

public record Import(TypedToken<ImportParser> typedToken, JavaClassMethod javaClassMethod , Identifier identifier){

  public static Import extract(TypedToken<ImportParser> typedToken) {
    String javaClassMethodOrClassName = typedToken.getChild(
        TokenPredicators.hasTag(ImportParser.javaClassMethodOrClassNameTag)).getToken().get();
    String identifier = typedToken.getChildWithParser(IdentifierParser.class).getToken().get();

    return new Import(typedToken , new JavaClassMethod(javaClassMethodOrClassName) , new Identifier(identifier));
  }
}