package org.unlaxer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.unlaxer.parser.MetaFunctionParser;
import org.unlaxer.parser.PseudoRootParser;
import org.unlaxer.reducer.CommittedReducer;
import org.unlaxer.reducer.MetaFunctionTokenReducer;
import org.unlaxer.util.Singletons;

public class Committed implements Serializable {

	private static final long serialVersionUID = -5413329267448242699L;

	List<Token> originalTokens;
	Optional<Token> collectedToken;
	boolean collected;

	public Committed(Committed committed) {
		this.originalTokens = new ArrayList<>(committed.getOriginalTokens());
		this.collectedToken = committed.getTokenOptional();
		this.collected = committed.isCollected();
	}

	public Committed(List<Token> originalTokens) {
		super();
		this.originalTokens = originalTokens;
		collected = false;
		collectedToken = Optional.empty();
	}

	public Committed(Token token, List<Token> originalTokens) {
		super();
		this.collectedToken = Optional.of(token);
		this.originalTokens = originalTokens;
		collected = true;
	}

	public Committed() {
		super();
		this.collectedToken = Optional.empty();
		collected = false;
		originalTokens = new ArrayList<>();
	}

	public Optional<Token> getTokenOptional() {
		return collectedToken;
	}

	public List<Token> getOriginalTokens() {
		return originalTokens;
	}

	public boolean isCollected() {
		return collected;
	}

	@SuppressWarnings("unchecked")
	public <T> T get() {
		return (T) (collected ? collectedToken.get() : originalTokens);
	}

	public boolean isEmpty() {
		return originalTokens.isEmpty();
	}

	public Token getConsumed(){
		return new Token(
				TokenKind.consumed,
				originalTokens.stream()
					.filter(TokenKind.consumed.passFilter)
					.collect(Collectors.toList()),
				collected ?
						collectedToken.get().parser:
							Singletons.get(PseudoRootParser.class),0);
	}


	/**
	 * @return root token. if collected == false , then create Chained token with original tokens
	 */
	public Token getRootToken(){
		return getRootToken(false);
	}

	/**
	 * @param reduceMetaFunctionParser if parameter == true , reduce {@link MetaFunctionParser}
	 * @return root token. if collected == false , then create Chained token with original tokens
	 */
	public Token getRootToken(boolean reduceMetaFunctionParser){
		if(reduceMetaFunctionParser){
			return new MetaFunctionTokenReducer().reduce(this);
		}
		Token token = collected ?
			collectedToken.get() :
			new Token(TokenKind.consumed, originalTokens, Singletons.get(PseudoRootParser.class),0);
		return token;
	}


	public Token getRootToken(CommittedReducer committedReducer){
		return committedReducer.reduce(this);
	}

	public void effectWithOriginalTokens(Consumer<Token> tokenConsumer) {
	  originalTokens.forEach(tokenConsumer);
	}

	public void effectWithCollectedToken(Consumer<Token> tokenConsumer) {
	  collectedToken.ifPresent(tokenConsumer);
	}

  public void effect(Consumer<Token> tokenConsumer) {
    effectWithOriginalTokens(tokenConsumer);
    effectWithCollectedToken(tokenConsumer);
	}
}