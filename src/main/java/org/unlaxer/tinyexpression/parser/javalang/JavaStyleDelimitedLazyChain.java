package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Name;
import org.unlaxer.RecursiveMode;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.reducer.TagBasedReducer.NodeKind;

public abstract class JavaStyleDelimitedLazyChain extends LazyChain {

  private static final long serialVersionUID = -324234946352474224L;

  public JavaStyleDelimitedLazyChain() {
    super();
  }

  public JavaStyleDelimitedLazyChain(Name name) {
    super(name);
  }

  @Override
  public Optional<RecursiveMode> getNotAstNodeSpecifier() {
    return Optional.empty();
  }

  static final JavaStyleDelimitor delimitor = new JavaStyleDelimitor();
  static {
    delimitor.addTag(NodeKind.notNode.getTag());
  }

  @Override
  public void prepareChildren(List<Parser> childrenContainer) {
    
    if(childrenContainer.isEmpty()){
      List<Parser> lazyParsers = getLazyParsers();
      childrenContainer.add(delimitor);
      for (Parser parser : lazyParsers) {
        childrenContainer.add(parser);
        childrenContainer.add(delimitor);
      }
    }
  }
}