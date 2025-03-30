package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.Name;
import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.TypedToken;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.AbstractParser;
import org.unlaxer.parser.ChildOccurs;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser.VariableDeclarations;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser.VariableInfo;

public class VariableDeclarationMatchedTokenParser extends AbstractParser{

  ExpressionType expressionType;

  public VariableDeclarationMatchedTokenParser(ExpressionType expressionType) {
    super();
    this.expressionType = expressionType;
  }

  @Override
  public void prepareChildren(Parsers childrenContainer) {
  }

  @Override
  public ChildOccurs getChildOccurs() {
    return ChildOccurs.none;
  }

  @Override
  public Parser createParser() {
    return this;
  }

  @Override
  public Parsed parse(ParseContext parseContext,TokenKind tokenKind,boolean invertMatch) {

    ExclusiveNakedVariableParser exclusiveNakedVariableParser =
        ExclusiveNakedVariableParser.SINGLETON.get();

    parseContext.getCurrent().setResetMatchedWithConsumed(false);

    parseContext.startParse(this, parseContext, tokenKind, invertMatch);
    parseContext.begin(this);

    Optional<VariableInfo> variableInfo_ = Optional.empty();

    boolean failed = false;
    while(true) {
      Parsed parsed = exclusiveNakedVariableParser.parse(parseContext,tokenKind,invertMatch);

      if(parsed.isStopped()){
        failed = true;
        break;
      }

      if (parsed.isFailed()) {
        failed = true;
        break;
      }
      TypedToken<VariableParser> typed = parsed.getRootToken().typed(VariableParser.class);

      String variableName = typed.apply(VariableParser::getVariableName);
      variableInfo_ = VariableDeclarations.SINGLETON.get(parseContext, variableName);

      if(variableInfo_.isEmpty()) {
        failed = true;
        break;
      }

      VariableInfo variableInfo = variableInfo_.get();
      if(expressionType.isNumber() && variableInfo.expressionType.isNumber()) {
        break;
      }
      if(variableInfo.expressionType != expressionType){
        failed = true;
      }
      break;
    }
    if(failed) {
      parseContext.rollback(this);
      parseContext.endParse(this, Parsed.FAILED , parseContext, tokenKind, invertMatch);
      return Parsed.FAILED;
    }

    Parsed committed = new Parsed(parseContext.commit(this,tokenKind));
    parseContext.endParse(this, committed, parseContext, tokenKind, invertMatch);
    variableInfo_.ifPresent(variableInfo->committed.effect(token->token.putExtraObject(VARIBALE_INFO, variableInfo)));
    return committed;
  }

  public static final Name VARIBALE_INFO = new Name(VariableInfo.class);

  public static Optional<VariableInfo> variableInfoOptional(TypedToken<VariableDeclarationMatchedTokenParser> token) {
    return token.getExtraObject(VARIBALE_INFO);
  }

  public static VariableInfo variableInfo(TypedToken<VariableDeclarationMatchedTokenParser> token) {
    return variableInfoOptional(token).get();
  }
}