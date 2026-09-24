// ubnfc runtime template: MemoTable
package org.unlaxer.tinyexpression.p4.ubnfc.generated.rt;

/** 解析セッション専用の boxing なし memo table。payload は生成側 arena への int handle。 */
public final class MemoTable {
    public static final int MAX_EXPR_ID = 0x00ff_ffff;
    public static final int MAX_MODE = 0xff;
    // 1 slot = 4 long: [expression key, position key, consumedEnd<<32 | matchedEnd, generation<<34 | outcome<<32 | payload].
    // 占有判定は世代印。表を消すのは世代を 1 つ進めるだけ（O(1)）で、容量に比例した書込みをしない。
    // outcome 1 = failure, 2 = success。1 slot = 32 B なので probe は必ず 1 cache line に収まる。
    private static final int STRIDE = 4;
    private static final int GENERATION_SHIFT = 34;
    private static final int MAX_GENERATION = (1 << 30) - 1;
    /** 解析ごとの大きな表を thread local に 1 組だけ持ち越す（内容は持ち越さない）。 */
    private static final ThreadLocal<long[]> POOL = new ThreadLocal<>();
    /**
     * thread ごとの世代印の発番。使い回した配列に残る古い印は必ず今の世代より小さいので、
     * 空き判定だけで区別できる（折り返しのときだけ pool を捨てて 1 から振り直す）。
     */
    private static final ThreadLocal<int[]> GENERATION = ThreadLocal.withInitial(() -> new int[1]);
    /** 持ち越す上限（32 MB）。これを超える表は毎回確保する。 */
    private static final int POOL_RETAIN_WORDS = 1 << 22;
    private long[] table;
    private int capacity;
    private int size;
    private long generation;

    public MemoTable() {
        this(16);
    }

    public MemoTable(int initialCapacity) {
        if (initialCapacity < 2 || initialCapacity > (1 << 29)) {
            throw new IllegalArgumentException("Capacity must be between 2 and 2^29");
        }
        int capacity = 2;
        while (capacity < initialCapacity) {
            capacity <<= 1;
        }
        generation = nextGeneration();
        allocate(capacity);
    }

    /** 1 から始まり単調に増える世代印。上限に達したら pool を捨てて 1 へ戻す。 */
    private static long nextGeneration() {
        int[] cell = GENERATION.get();
        if (cell[0] >= MAX_GENERATION) { cell[0] = 0; POOL.remove(); }
        return ++cell[0];
    }

    /** 未登録は -1。返した slot は次の put/clear まで有効。 */
    public int get(int exprId, int consumed, int matched, int mode, int stateVersion) {
        long expression = expressionKey(exprId, mode, stateVersion);
        long position = positionKey(consumed, matched);
        int slot = locate(expression, position);
        return occupied(table[slot * STRIDE + 3]) ? slot : -1;
    }

    public int put(int exprId, int consumed, int matched, int mode, int stateVersion,
                   boolean ok, int consumedEnd, int matchedEnd, int payload) {
        long expression = expressionKey(exprId, mode, stateVersion);
        long position = positionKey(consumed, matched);
        int slot = locate(expression, position);
        if (!occupied(table[slot * STRIDE + 3])) {
            if (size == capacity / 2) {
                grow();
                slot = locate(expression, position);
            }
            table[slot * STRIDE] = expression;
            table[slot * STRIDE + 1] = position;
            size++;
        }
        table[slot * STRIDE + 2] = ((long) consumedEnd << 32) | (matchedEnd & 0xffff_ffffL);
        table[slot * STRIDE + 3] = (generation << GENERATION_SHIFT) | ((long) (ok ? 2 : 1) << 32) | (payload & 0xffff_ffffL);
        return slot;
    }

    public boolean ok(int slot) {
        checkSlot(slot);
        return ((table[slot * STRIDE + 3] >>> 32) & 3) == 2;
    }

    public int consumedEnd(int slot) {
        checkSlot(slot);
        return (int) (table[slot * STRIDE + 2] >>> 32);
    }

    public int matchedEnd(int slot) {
        checkSlot(slot);
        return (int) table[slot * STRIDE + 2];
    }

    public int payload(int slot) {
        checkSlot(slot);
        return (int) table[slot * STRIDE + 3];
    }

    /** 世代を 1 つ進めるだけ。残っている印は全て古い世代になるので、書込みは 0 回。 */
    public void clear() {
        generation = nextGeneration();
        size = 0;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return capacity;
    }

    private static long expressionKey(int exprId, int mode, int stateVersion) {
        // 24 + 8 + 32 bits. Reject overflow rather than silently aliasing distinct keys.
        if ((exprId & ~MAX_EXPR_ID) != 0 || (mode & ~MAX_MODE) != 0) {
            throw new IllegalArgumentException("ExprId requires 24 bits and mode requires 8 bits");
        }
        return ((long) exprId << 40) | ((long) mode << 32) | (stateVersion & 0xffff_ffffL);
    }

    private static long positionKey(int consumed, int matched) {
        if (consumed < 0 || matched < 0) {
            throw new IllegalArgumentException("Negative UTF-16 position");
        }
        return ((long) consumed << 32) | (matched & 0xffff_ffffL);
    }

    private boolean occupied(long outcome) {
        return (outcome >>> GENERATION_SHIFT) == generation;
    }

    private int locate(long expression, long position) {
        // 2 つの乗算は互いに依存しないので同時に走る（連鎖させると遅延がそのまま足し算になる）。
        // 上位 bit を使うので 2 冪 mask でも位置と式 id の下位 bit 偏りを拾わない。
        // 同じ容量での探索歩数も連鎖版より少ない（P4 complex で 1,613 → 1,401 歩 / 1,680 probe）。
        long hash = expression * 0x9e37_79b9_7f4a_7c15L + position * 0xbf58_476d_1ce4_e5b9L;
        int mask = capacity - 1;
        int slot = (int) (hash >>> 32) & mask;
        long[] t = table;
        while (occupied(t[slot * STRIDE + 3])
                && (t[slot * STRIDE] != expression || t[slot * STRIDE + 1] != position)) {
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    private void grow() {
        if (capacity == (1 << 29)) {
            throw new IllegalStateException("Memo table capacity exhausted");
        }
        long[] old = table;
        int oldCapacity = capacity;
        // 世代は据え置く（印ごと複製するので、印を振り直さずに移せる）。
        allocate(oldCapacity << 1);
        for (int i = 0; i < oldCapacity; i++) {
            if (occupied(old[i * STRIDE + 3])) {
                int slot = locate(old[i * STRIDE], old[i * STRIDE + 1]);
                System.arraycopy(old, i * STRIDE, table, slot * STRIDE, STRIDE);
            }
        }
    }

    /**
     * 解析の終わりに表を pool へ返し、最小の表に戻す。内容は捨てるので次の解析の観測結果は変わらない。
     * 返した後も put/get は（小さい表で）正しく動く。
     */
    public void release() {
        long[] current = table;
        this.capacity = 2;
        table = new long[2 * STRIDE];
        size = 0;
        generation = nextGeneration();
        if (current.length > POOL_RETAIN_WORDS) return;
        long[] pooled = POOL.get();
        if (pooled == null || pooled.length < current.length) POOL.set(current);
    }

    private void allocate(int capacity) {
        this.capacity = capacity;
        int words = capacity * STRIDE;
        long[] pooled = POOL.get();
        // 同じ大きさ（最大 8 倍まで）の表があれば、消さずにそのまま使う（残る印は古い世代）。
        if (pooled != null && pooled.length >= words && pooled.length <= (long) words * 8) {
            POOL.remove();
            table = pooled;
            return;
        }
        table = new long[words];
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= capacity || !occupied(table[slot * STRIDE + 3])) {
            throw new IndexOutOfBoundsException("No memo entry at slot " + slot);
        }
    }
}
