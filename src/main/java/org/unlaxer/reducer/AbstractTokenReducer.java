package org.unlaxer.reducer;

import java.util.ArrayList;
import java.util.List;

import org.unlaxer.Committed;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.parser.ChildOccurs;
import org.unlaxer.parser.MetaFunctionParser;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.PseudoRootParser;
import org.unlaxer.util.Singletons;

public abstract class AbstractTokenReducer implements CommittedReducer {
	
	public abstract boolean doReduce(Parser parser);


	public Token reduce(Committed committed) {
		Token token = committed.isCollected() ? //
				committed.getTokenOptional().get() : //
				new Token(//
						TokenKind.consumed, //
						committed.getOriginalTokens(), //
						Singletons.get(PseudoRootParser.class), //
						0);

		// TokenPrinter.output(token, System.out, 0, DetailLevel.detail,
		// true);

		List<Token> children = new ArrayList<>();

		if (doReduce(token.getParser())) {
			PseudoRootParser root = new PseudoRootParser();
			root.getChildren().add(token.parser);
			Token newRootToken = new Token(//
					token.getTokenKind(), //
					token.getRangedString(), //
					root);
			newRootToken.addChildren(token);
			token = newRootToken;
		}

		for (Token childToken : token.getAstNodeChildren()) {
			if(childToken.tokenString.isEmpty()) {
				continue;
			}
			children.addAll(reduce(childToken));
		}
		token.getAstNodeChildren().clear();
		token.getAstNodeChildren().addAll(children);

		// TokenPrinter.output(token, System.out, 0, DetailLevel.detail,
		// true);

		return token;
	}

	List<Token> reduce(Token token) {

		// TokenPrinter.output(token, System.out, 0, DetailLevel.detail,
		// false);
		// System.out.println();

		if (token.getAstNodeChildren().isEmpty()) {
			return reduceWithLeaf(token);
		}
		List<Token> tokens = new ArrayList<Token>();

		token.getAstNodeChildren().stream().map(this::reduce)
			.forEach(tokens::addAll);

		if (doReduce(token.parser)) {
			return tokens;
		}
		token.getAstNodeChildren().clear();
		token.getAstNodeChildren().addAll(tokens);
		List<Token> tokenContainer = new ArrayList<Token>();
		tokenContainer.add(token);
		return tokenContainer;

	}

	private List<Token> reduceWithLeaf(Token token) {

		ArrayList<Token> tokens = new ArrayList<Token>();
		if (doReduce(token.parser)) {
			return tokens;
		}

		List<Parser> childParsers = token.parser.getChildren();

		if (childParsers.isEmpty()) {
			if (false == doReduce(token.parser)) {
				tokens.add(token);
			}
			return tokens;
		}

		List<Parser> parsers = new ArrayList<Parser>();
		for (Parser childParser : childParsers) {
			parsers.addAll(reduce(childParser));
		}

		token.parser.getChildren().clear();
		token.parser.getChildren().addAll(parsers);
		tokens.add(token);

		return tokens;
	}

	private List<Parser> reduce(Parser parser) {

		List<Parser> parsers = new ArrayList<>();

		if (false == parser instanceof MetaFunctionParser) {
			parsers.add(parser);
		}
		ChildOccurs childOccurs = parser.getChildOccurs();
		if (childOccurs.isSingle()) {

			parsers.addAll(reduce(parser.getChild()));

		} else if (childOccurs.isMulti()) {

			for (Parser childParser : parser.getChildren()) {
				parsers.addAll(reduce(childParser));
			}
		}
		return parsers;
	}

}