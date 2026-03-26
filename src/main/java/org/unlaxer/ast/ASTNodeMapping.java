package org.unlaxer.ast;

import org.unlaxer.Name;
import org.unlaxer.tinyexpression.parser.Opecode;

public class ASTNodeMapping{

    ASTNodeMappings astNodeMappings;
    final Name name;
    final ASTNodeKind astNodeKind;
    final Opecode opecode;
    final boolean root;

//    public ASTNodeMapping(org.unlaxer.Name name, ASTNodeKind astNodeKind,boolean root) {
//      this(name,astNodeKind,null , root);
//    }
    public ASTNodeMapping(org.unlaxer.Name name, ASTNodeKind astNodeKind, Opecode opecode , boolean root) {
      super();
      this.name = name;
      this.astNodeKind = astNodeKind;
      this.opecode = opecode;
      this.root = root;
    }
    public ASTNodeKind astNodeKind() {
      return astNodeKind;
    }
//    public Optional<Opecode> opecode() {
//      return Optional.ofNullable(opecode);
//    }
    public Opecode opecode() {
      return opecode;
    }
    public Name name() {
      return name;
    }
    public ASTNodeMappings astNodeMappings() {
      return astNodeMappings;
    }
    public void setAstNodeMappings(ASTNodeMappings astNodeMappings) {
      this.astNodeMappings = astNodeMappings;
    }
    public boolean isRoot() {
      return root;
    }
  }