package org.unlaxer;

import java.util.List;

import org.unlaxer.ast.ASTNodeKind;
import org.unlaxer.parser.Parser;

public interface ASTNodeContainer{
  
  public Parser setASTNodeKind(Name name ,ASTNodeKind astNodeKind);
  
  public ASTNodeKind astNodeKind();
  
  public List<ASTNodeKindAndParser> astNodeKind(Name name);
  
  public static record ASTNodeKindAndParser(ASTNodeKind astNodeKind,Parser parser) {}


}
