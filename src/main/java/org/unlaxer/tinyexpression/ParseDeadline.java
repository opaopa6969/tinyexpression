package org.unlaxer.tinyexpression;

import java.util.function.Supplier;

import org.unlaxer.Name;
import org.unlaxer.TokenList;
import org.unlaxer.context.ParseContext;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.listener.TransactionListener;
import org.unlaxer.parser.Parser;

/**
 * 協調的パース期限。深くネストした式では combinator のバックトラックが指数的に
 * なり、パースが実質終了しないことがある (issue #19, #20)。
 *
 * <p>スレッドも割り込みも使わない: unlaxer 3.0.4 以降、登録 listener の
 * {@code onBegin} は全パーサーのトランザクション begin で呼ばれるため、期限超過時に
 * throw すればパースループは同一スレッドで巻き戻る。
 *
 * <p>使い方: 期限を掛けたい呼び出しを {@link #callWithDeadline(long, Supplier)} で
 * 包む。その内側で構築される {@link PreConstructedCalculator} は
 * {@link #installIfSet(ParseContext)} 経由で自動的に期限 listener を持つ。
 * 期限未設定のスレッドでは何も起きない。
 */
public final class ParseDeadline {

  private ParseDeadline() {}

  /** 期限超過。呼び出し側はフォールバック経路に切り替えること。 */
  public static final class Exceeded extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Exceeded(String message) {
      super(message);
    }
  }

  private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

  private static final Name LISTENER_NAME = Name.of(ParseDeadline.class, "parseDeadline");

  /**
   * {@code deadlineNanos} ({@link System#nanoTime()} 基準の絶対時刻、0 以下で無期限)
   * を現在スレッドに設定して {@code body} を実行する。ネスト時は外側の期限を保存・復元する。
   */
  public static <T> T callWithDeadline(long deadlineNanos, Supplier<T> body) {
    Long previous = DEADLINE_NANOS.get();
    if (deadlineNanos > 0L) {
      DEADLINE_NANOS.set(deadlineNanos);
    } else {
      DEADLINE_NANOS.remove();
    }
    try {
      return body.get();
    } finally {
      if (previous == null) {
        DEADLINE_NANOS.remove();
      } else {
        DEADLINE_NANOS.set(previous);
      }
    }
  }

  /** timeoutMillis (0 以下で無期限) から期限を計算する補助。 */
  public static long deadlineFromTimeoutMillis(long timeoutMillis) {
    if (timeoutMillis <= 0L) {
      return 0L;
    }
    return System.nanoTime() + timeoutMillis * 1_000_000L;
  }

  /**
   * 現在スレッドに期限が設定されていれば、期限超過で throw する listener を
   * {@code context} に登録する。未設定なら何もしない。
   */
  public static void installIfSet(ParseContext context) {
    Long deadline = DEADLINE_NANOS.get();
    if (deadline == null || deadline <= 0L) {
      return;
    }
    long deadlineNanos = deadline;
    context.addTransactionListener(LISTENER_NAME, new TransactionListener() {
      @Override public void setLevel(OutputLevel level) {}
      @Override public void onOpen(ParseContext parseContext) {}
      @Override public void onBegin(ParseContext parseContext, Parser parser) {
        if (System.nanoTime() > deadlineNanos) {
          throw new Exceeded("parse exceeded deadline; caller should fall back");
        }
      }
      @Override public void onCommit(ParseContext parseContext, Parser parser, TokenList committedTokens) {}
      @Override public void onRollback(ParseContext parseContext, Parser parser, TokenList rollbackedTokens) {}
      @Override public void onClose(ParseContext parseContext) {}
    });
  }
}
