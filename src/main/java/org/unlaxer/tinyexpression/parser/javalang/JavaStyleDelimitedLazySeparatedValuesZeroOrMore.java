package org.unlaxer.tinyexpression.parser.javalang;

import java.util.function.Supplier;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.LazyZeroOrMore;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.reducer.TagBasedReducer.NodeKind;

public abstract class JavaStyleDelimitedLazySeparatedValuesZeroOrMore extends LazyZeroOrMore {

  public JavaStyleDelimitedLazySeparatedValuesZeroOrMore() {
    super();
  }

  public JavaStyleDelimitedLazySeparatedValuesZeroOrMore(Name name) {
    super(name);
  }


  static final JavaStyleDelimitor delimitor = new JavaStyleDelimitor();
  static {
    delimitor.addTag(NodeKind.notNode.getTag());
  }

  @Override
  public void prepareChildren(Parsers childrenContainer) {
    
    if(childrenContainer.isEmpty()){
      Parser targetParser = getLazyParser().get();
      Parser separatorParser = getSeparatorParser().get();
      
      childrenContainer.add(
          new Chain(
              delimitor,
              targetParser,
              delimitor,
              new ZeroOrMore(
                  new Chain(
                      separatorParser,
                      delimitor,
                      targetParser,
                      delimitor
                  )
              )   
          )
      );
    }
  }
  
  
  public abstract Supplier<Parser> getSeparatorParser();
  
}