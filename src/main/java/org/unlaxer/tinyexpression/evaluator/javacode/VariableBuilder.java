package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.IfNotExistsParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.SetterParser;
import org.unlaxer.tinyexpression.parser.VariableParser;

public class VariableBuilder {
 
   public static void build(TokenCodeBuilder parentBuilder , SimpleJavaCodeBuilder builder,
       TypedToken<VariableParser> token ,
       TinyExpressionTokens tinyExpressionTokens , Class<? extends SetterParser> setterParserClass,
       String defaultValue , String getMethod , String setAndGetMethod , ExpressionType variableType) {
     
     VariableParser variableParser = token.getParser();
     
     boolean isOptional = true;
     
     List<Token> variableDeclarationsTokens = tinyExpressionTokens.getVariableDeclarationTokens();
     

     String variableName = variableParser.getVariableName(token);
     
     boolean isMatch =false;
     for (Token declarationTtoken : variableDeclarationsTokens) {
       TypedToken<? extends VariableParser> nakedVariableToken = 
           declarationTtoken.getChildWithParserTyped(NakedVariableParser.class);
       VariableParser variabvleParser = nakedVariableToken.getParser(VariableParser.class);
       
       String _variableName = variabvleParser.getVariableName(nakedVariableToken);
       
       if(_variableName.equals(variableName)) {
         Optional<Token> setterToken = declarationTtoken.getChildWithParserAsOptional(setterParserClass);
         if(setterToken.isEmpty()) {
           continue;
         }
         Token _setterToken = setterToken.get();
         Token expression = _setterToken.getChild(TokenPredicators.parserImplements(ExpressionInterface.class));
         Optional<Token> ifNotExists = _setterToken.getChildWithParserAsOptional(IfNotExistsParser.class);
         
         SimpleJavaCodeBuilder simpleJavaCodeBuilder = new SimpleJavaCodeBuilder();
         parentBuilder.build(simpleJavaCodeBuilder, expression, tinyExpressionTokens);
         String expseeionString = simpleJavaCodeBuilder.builder.toString();
//     String expseeionString = expression.getToken().orElseThrow();
         
         
         
         if(ifNotExists.isPresent()) {
           
           builder.append("calculateContext."+getMethod+"(").w(variableName).append(").orElse("+expseeionString+")");
         }else {
           builder.append("calculateContext."+setAndGetMethod+"(").w(variableName).append(","+expseeionString+")");
           isOptional = false;
         }
         isMatch = true;
         break;
       }
     }
     if(false == isMatch) {
       builder.append("calculateContext."+getMethod+"(").w(variableName).append(").orElse("+defaultValue+")");
     }
     
     if(variableType.isNumber() && isOptional) {
    	 builder.append(".floatValue()");
     }
   }

 }