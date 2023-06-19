package org.unlaxer.tinyexpression.formatter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.NumberCaseExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberCaseFactorParser;
import org.unlaxer.tinyexpression.parser.NumberDefaultCaseFactorParser;
import org.unlaxer.tinyexpression.parser.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.FormulaParser;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberMatchExpressionParser;
import org.unlaxer.tinyexpression.parser.RightCurlyBraceParser;

public class Formatter {
  
  public static String format(String expression) {
    
    
    try(ParseContext parseContext = new ParseContext(new StringSource(expression))){
      FormatterContext formatterContext = new FormatterContext(0);
      
      FormulaParser formulaParser = Parser.get(FormulaParser.class);
      Parsed parsed = formulaParser.parse(parseContext);
      
      if(false == parsed.isSucceeded()) {
        return expression;
      }
      Token rootToken = parsed.getRootToken(true);
      
      render(formatterContext, rootToken);
      
      return formatterContext.toString();
      
    }catch (Exception e) {
      e.printStackTrace();
      return expression;
    }
  }
  
  static Comparator<Token> tokenComparator = (o1,o2)->{
    return o1.getTokenRange().compareTo(o2.tokenRange);
  };
  
  public static void renderIf(FormatterContext context , Token token) {
    
    FormatterContext formatterContext = new FormatterContext(context.startPosition());
    
    List<Token> filteredChildren = token.filteredChildren;
    for (Token child : filteredChildren) {
      if(child.getParser() instanceof NumberExpressionParser) {
//        formatterContext.n();
        formatterContext.increment();
        render(formatterContext, child);
//        formatterContext.n();
        formatterContext.decrement();
      }else {
        render(formatterContext, child);
      }
    }
    context.append(formatterContext.toString());
  }
  
  public static void renderCaseExpression(FormatterContext context , Token token) {
    
    List<Token> filteredChildren = token.filteredChildren.stream()
        .filter(child->child.getParser() instanceof NumberCaseFactorParser)
        .collect(Collectors.toList());
    Iterator<Token> iterator = filteredChildren.iterator();
    while (iterator.hasNext()) {
      Token child = (Token) iterator.next();
      render(context, child);
      context
          .append(",")
          .n()
          .tab();
    }
  }

  
  public static void renderMatch(FormatterContext context , Token token) {
    
    FormatterContext formatterContext = new FormatterContext(context.startPosition());
    
    List<Token> filteredChildren = token.filteredChildren;
    for (Token child : filteredChildren) {
      if(child.getParser() instanceof NumberCaseExpressionParser) {
        formatterContext.increment();
      }
      if(child.getParser() instanceof RightCurlyBraceParser) {
        formatterContext.decrement();
      }
      render(formatterContext, child);
    }
    context.append(formatterContext.toString());
  }
  
  public static void render(FormatterContext context , Token token) {
    
    if(token.getParser() instanceof IfExpressionParser) {
      renderIf(context, token);
      return;
    }
    
    if(token.getParser() instanceof NumberMatchExpressionParser) {
      renderMatch(context, token);
      return;
    }
    
    if(token.getParser() instanceof NumberCaseExpressionParser) {
      renderCaseExpression(context, token);
      return;
    }
    
    //FIXME! DefaultCaseFactorParser starts ",", to change starts default->
    if(token.getParser() instanceof NumberDefaultCaseFactorParser) {
      context
        .append("default->");
        render(context, token.filteredChildren.get(3));
        return;
    }
    
    List<Token> tokens = new ArrayList<>(token.filteredChildren);
    if(tokens.size() ==0) {
      token.getRangedString().token.ifPresent(context::append);
      return;
    }
      
    tokens.sort(tokenComparator);
    for (Token child: tokens) {
      render(context, child);
    }
     
  }
  
  public static class FormatterContext{
    final String tab="  ";
    
    int tabSize = 2;
    int level = 0;
    Map<Integer,Integer> startPositionByLevel = new HashMap<>();
    Map<Integer,Integer> positionByLevel = new HashMap<>();
    StringBuilder builder = new StringBuilder();
    
    public FormatterContext(int startPosition) {
      super();
      positionByLevel.put(level, startPosition);
      startPositionByLevel.put(level, startPosition);
    }
    
    public FormatterContext append(String text) {
      builder.append(text);
      Integer position = positionByLevel.get(level);
      positionByLevel.put(level, position+text.length());
      return this;
    }
    
    public FormatterContext appendWithSpace(String text) {
      append(text+" ");
      return this;
    }
    
    public FormatterContext increment() {
      n();
      Integer positionByCurrent = startPositionByLevel.get(level);
      level++;
      startPositionByLevel.put(level, positionByCurrent+tabSize);
      positionByLevel.put(level, positionByLevel.get(level-1)+tabSize);
      tab();
      return this;
    }
    
    public FormatterContext decrement() {
      n();
      level--;
      tab();
      return this;
    }
    
    public FormatterContext n() {
      builder.append("\n");
      return this;
    }
    
    public int startPosition() {
      return startPositionByLevel.get(level);
    }
    
    public FormatterContext tab() {
      if(startPosition()==0) {
        return this;
      }
      builder.append(String.format("%"+startPosition()+"s", ""));
      return this;
    }
    
    public String toString() {
      return builder.toString();
    }
    
    public FormatterContext trimLast() {
      builder.deleteCharAt(builder.length()-1);
      return this;
    }
  }

}
