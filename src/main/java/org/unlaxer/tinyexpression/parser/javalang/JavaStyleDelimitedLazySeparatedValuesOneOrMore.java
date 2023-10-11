package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;
import java.util.function.Supplier;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.LazyOneOrMore;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.reducer.TagBasedReducer.NodeKind;

public abstract class JavaStyleDelimitedLazySeparatedValuesOneOrMore extends LazyOneOrMore {

  public JavaStyleDelimitedLazySeparatedValuesOneOrMore() {
    super();
  }

  public JavaStyleDelimitedLazySeparatedValuesOneOrMore(Name name) {
    super(name);
  }


  static final JavaStyleDelimitor delimitor = new JavaStyleDelimitor();
  static {
    delimitor.addTag(NodeKind.notNode.getTag());
  }

  @Override
  public void prepareChildren(List<Parser> childrenContainer) {
    
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