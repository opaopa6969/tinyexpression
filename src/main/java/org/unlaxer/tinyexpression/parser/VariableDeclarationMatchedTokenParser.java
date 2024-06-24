package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

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
    
    

    boolean failed = false;
    while(true) {
      Parsed parsed = exclusiveNakedVariableParser.parse(parseContext,tokenKind,invertMatch);
      
      if(parsed.isStopped()){
        break;
      }
      
      if (parsed.isFailed()) {
        failed = true;
        break;
      }
      TypedToken<VariableParser> typed = parsed.getRootToken().typed(VariableParser.class);
      
      String variableName = exclusiveNakedVariableParser.getVariableName(typed);
      Optional<VariableInfo> optional = VariableDeclarations.SINGLETON.get(parseContext, variableName);
      
      if(optional.isEmpty()) {
        failed = true;
        break;
      }
      
      VariableInfo variableInfo = optional.get();
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
    return committed;
  }
}