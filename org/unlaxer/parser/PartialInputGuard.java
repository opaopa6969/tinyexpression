package org.unlaxer.parser;

import org.unlaxer.Parsed;
import org.unlaxer.Parsed.Status;
import org.unlaxer.Source;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;

/**
 * Chain の子として配置し、残りの入力が空の場合に {@link Status#stopped} を返す。
 * これにより Chain の後続子パーサーがスキップされ、それまでの部分結果がコミットされる。
 *
 * <p>主な用途: IDE やLSPでユーザーが入力途中のソースに対して、
 * 部分的なトークン木（シンタックスハイライト・構造情報等）を得る。</p>
 *
 * <pre>{@code
 * // 使用例: if文パーサーの各要素の間に配置
 * new Chain(
 *     KeywordIfParser,
 *     OpenParenParser,
 *     new PartialInputGuard(),   // ← 入力が "if (" で終わっていたらここで stopped
 *     ExpressionParser,
 *     new PartialInputGuard(),   // ← 入力が "if (x > 1" で終わっていたらここで stopped
 *     CloseParenParser,
 *     BlockParser
 * )
 * }</pre>
 */
public class PartialInputGuard extends AbstractParser {

	private static final long serialVersionUID = 1L;

	@Override
	public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {

		parseContext.startParse(this, parseContext, tokenKind, invertMatch);
		parseContext.begin(this);

		Source remain = parseContext.getRemain(TokenKind.consumed);
		if (remain.isEmpty()) {
			Parsed committed = new Parsed(parseContext.commit(this, TokenKind.matchOnly));
			committed.status = Status.stopped;
			parseContext.endParse(this, committed, parseContext, tokenKind, invertMatch);
			return committed;
		}

		Parsed committed = new Parsed(parseContext.commit(this, TokenKind.matchOnly));
		parseContext.endParse(this, committed, parseContext, tokenKind, invertMatch);
		return committed;
	}

	@Override
	public void prepareChildren(Parsers childrenContainer) {
	}

	@Override
	public ChildOccurs getChildOccurs() {
		return ChildOccurs.none;
	}

	@Override
	public Parser createParser() {
		return this;
	}
}
