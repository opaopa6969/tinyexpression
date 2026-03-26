package org.unlaxer.parser;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.unlaxer.Name;
import org.unlaxer.Parsed;
import org.unlaxer.RecursiveMode;
import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.ast.ASTNodeKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.parser.Opecode;

public class ParentHolderParser implements Parser{
    Supplier<? extends Parser> inner;
    Parser parent;
    
    public ParentHolderParser(Class<? extends Parser> inner) {
      super();
      this.inner = ()->Parser.get(inner);
    }

    public Parser addTag(Tag... addings) {
      return inner.get().addTag(addings);
    }

    public Parser addTagRecurciveChildrenOnly(Tag... addeds) {
      return inner.get().addTagRecurciveChildrenOnly(addeds);
    }

    public boolean getInvertMatchFromParent() throws IllegalStateException {
      return inner.get().getInvertMatchFromParent();
    }

    public Parser setASTNodeKind(ASTNodeKind astNodeKind, Opecode... targetOpecodes) {
      return inner.get().setASTNodeKind(astNodeKind, targetOpecodes);
    }

    public Optional<Parser> findFirstToChild(Predicate<Parser> predicate) {
      return inner.get().findFirstToChild(predicate);
    }

    public Optional<Parser> findFirstFromRoot(Predicate<Parser> predicate) {
      return inner.get().findFirstFromRoot(predicate);
    }

    public String getParentPath() {
      return inner.get().getParentPath();
    }

    public Parser addTagRecurcive(Tag... addeds) {
      return inner.get().addTagRecurcive(addeds);
    }

    public boolean hasTag(Tag tag) {
      return inner.get().hasTag(tag);
    }

    public ASTNodeKind astNodeKind() {
      return inner.get().astNodeKind();
    }

    public String getPath() {
      return inner.get().getPath();
    }

    public Optional<Parser> findFirstToParent(Predicate<Parser> predicate) {
      return inner.get().findFirstToParent(predicate);
    }

    public Parser setOperator(Opecode opecode) {
      return inner.get().setOperator(opecode);
    }

    public Stream<Parser> findToChild(Predicate<Parser> predicate) {
      return inner.get().findToChild(predicate);
    }

    public Parser removeTag(Tag... removes) {
      return inner.get().removeTag(removes);
    }

    public Stream<Parser> findFromRoot(Predicate<Parser> predicate) {
      return inner.get().findFromRoot(predicate);
    }

    public Parser addTag(boolean recursive, RecursiveMode recursiveMode, Tag... addeds) {
      return inner.get().addTag(recursive, recursiveMode, addeds);
    }

    public String getPath(boolean containCallerParser) {
      return inner.get().getPath(containCallerParser);
    }

    public Parser setOperand(Opecode... targetOpecodes) {
      return inner.get().setOperand(targetOpecodes);
    }

    public Set<Tag> getTags() {
      return inner.get().getTags();
    }

    public Optional<Opecode> opecode() {
      return inner.get().opecode();
    }

    public Parsers flatten() {
      return inner.get().flatten();
    }

    public Set<Opecode> targetOpecodes() {
      return inner.get().targetOpecodes();
    }

    public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
      return inner.get().parse(parseContext, tokenKind, invertMatch);
    }

    public List<Name> getNamePath() {
      return inner.get().getNamePath();
    }

    public void flatten(RecursiveMode recursiveMode, Parsers parsers) {
      inner.get().flatten(recursiveMode, parsers);
    }

    public List<Name> getParentNamePath() {
      return inner.get().getParentNamePath();
    }

    public Parser removeTagRecurciveChildrenOnly(Tag... removes) {
      return inner.get().removeTagRecurciveChildrenOnly(removes);
    }

    public Parsed parse(ParseContext parseContext) {
      return inner.get().parse(parseContext);
    }

    public List<Name> getNamePath(NameKind nameKind) {
      return inner.get().getNamePath(nameKind);
    }

    public Parser removeTagRecurcive(Tag... removes) {
      return inner.get().removeTagRecurcive(removes);
    }

    public TokenKind getTokenKind() {
      return inner.get().getTokenKind();
    }

    public List<Name> getParentNamePath(NameKind nameKind) {
      return inner.get().getParentNamePath(nameKind);
    }

    public Parser removeTag(boolean recursive, RecursiveMode recursiveMode, Tag... removes) {
      return inner.get().removeTag(recursive, recursiveMode, removes);
    }

    public boolean forTerminalSymbol() {
      return inner.get().forTerminalSymbol();
    }

    public List<Name> getNamePath(NameKind nameKind, boolean containCallerParser) {
      return inner.get().getNamePath(nameKind, containCallerParser);
    }

    public Name getName(NameKind nameKind) {
      return inner.get().getName(nameKind);
    }

    public Parsers findParents(Predicate<Parser> predicate) {
      return inner.get().findParents(predicate);
    }

    public Name getName() {
      return inner.get().getName();
    }

    public boolean equalsByClass(Parser other) {
      return inner.get().equalsByClass(other);
    }

    public Name getComputedName() {
      return inner.get().getComputedName();
    }

    public Parser getChild() {
      return inner.get().getChild();
    }

    @Override
    public Optional<Parser> getParent() {
      return Optional.ofNullable(parent);
    }

    public Parser getThisParser() {
      return inner.get().getThisParser();
    }

    public Stream<Parser> getPathStream(boolean containCallerParser) {
      return inner.get().getPathStream(containCallerParser);
    }

    public Parsers getChildren() {
      return inner.get().getChildren();
    }

    public Parsers findParents(Predicate<Parser> predicate, boolean containCallerParser) {
      return inner.get().findParents(predicate, containCallerParser);
    }

    public void prepareChildren(Parsers childrenContainer) {
      inner.get().prepareChildren(childrenContainer);
    }

    public NodeReduceMarker getNodeReduceMarker() {
      return inner.get().getNodeReduceMarker();
    }

    @Override
    public void setParent(Parser parent) {
      this.parent = parent;
    }

    public Parser getRoot() {
      return inner.get().getRoot();
    }

    public ChildOccurs getChildOccurs() {
      return inner.get().getChildOccurs();
    }

    public Parsers getSiblings(boolean containsMe) {
      return inner.get().getSiblings(containsMe);
    }

    public Optional<Parser> getParser(Name name) {
      return inner.get().getParser(name);
    }

    public boolean isTokenParsedByThisParser(Token token) {
      return inner.get().isTokenParsedByThisParser(token);
    }

    public void checkTokenParsedByThisParser(Token token) {
      inner.get().checkTokenParsedByThisParser(token);
    }

    public Map<String, Object> objectByName() {
      return inner.get().objectByName();
    }

    public Parser removeObject(Name name) {
      return inner.get().removeObject(name);
    }

    public Parser removeObject(String name) {
      return inner.get().removeObject(name);
    }

    public <T> T getObject(Name name, Class<T> clazz) {
      return inner.get().getObject(name, clazz);
    }

    public <T> T getObject(String name, Class<T> clazz) {
      return inner.get().getObject(name, clazz);
    }

    public Object getObject(Name name) {
      return inner.get().getObject(name);
    }

    public Object getObject(String name) {
      return inner.get().getObject(name);
    }

    public Parser putObject(Name name, Object object) {
      return inner.get().putObject(name, object);
    }

    public Parser putObject(String name, Object object) {
      return inner.get().putObject(name, object);
    }

    public Parser putObject(Object object) {
      return inner.get().putObject(object);
    }
  }