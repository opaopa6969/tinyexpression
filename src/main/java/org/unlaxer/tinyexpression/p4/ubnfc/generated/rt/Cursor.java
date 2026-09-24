// ubnfc runtime template: Cursor
package org.unlaxer.tinyexpression.p4.ubnfc.generated.rt;

/** UTF-16 単位の二重カーソル。生成コードは matched >= consumed を維持する。 */
public final class Cursor {
    public int consumed;
    public int matched;

    public Cursor() {}

    public Cursor(int consumed, int matched) {
        if (consumed < 0 || matched < consumed) {
            throw new IllegalArgumentException("Expected 0 <= consumed <= matched");
        }
        this.consumed = consumed;
        this.matched = matched;
    }

    public void advance(int n) {
        checkAdvance(n);
        int nextConsumed = Math.addExact(consumed, n);
        int nextMatched = Math.addExact(matched, n);
        consumed = nextConsumed;
        matched = nextMatched;
    }

    public void advanceMatchedOnly(int n) {
        checkAdvance(n);
        matched = Math.addExact(matched, n);
    }

    public void resetMatchedToConsumed() {
        matched = consumed;
    }

    public long checkpoint() {
        return ((long) consumed << 32) | (matched & 0xffff_ffffL);
    }

    /** このカーソルまたは同じ位置体系のカーソルが作った checkpoint を復元する。 */
    public void restore(long checkpoint) {
        consumed = (int) (checkpoint >>> 32);
        matched = (int) checkpoint;
    }

    private static void checkAdvance(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Negative advance: " + n);
        }
    }
}
