// ubnfc runtime template: Diagnostics
package org.unlaxer.tinyexpression.p4.ubnfc.generated.rt;

import java.util.Arrays;
import java.util.Objects;

/**
 * 構文診断。semantic 診断の rollback とは独立し、成功 rule 内の失敗も保持する。
 *
 * <p>実装: 失敗は (a) 解析全体の accumulator（global）へ即時に、(b) 最内 frame の要約（最遠位置・cursor・
 * rule stack）と、その frame の expected 集合へ O(1) で記録する。expected 集合は frame ごとの区間を
 * 1 本の共有 stack（{@link #expectedStack}）に積み、frame を離れるときに「最遠位置が親より遠ければ置換、
 * 同じなら合併、手前なら破棄」で親へ吸収する。区間の長さは最遠位置の候補数（数件）なので、吸収は O(k)。
 * 負の先読みの restore は global・frame 要約・expected stack をまとめて checkpoint の内容へ戻す
 * （先読み内で memo 化された rule の snapshot は作成時に複製済みなので影響を受けない）。
 * 観測結果は「失敗ごとに全 frame へ即時記録する」実装と同値。
 *
 * <p>rule stack は不変の親リンク連結リスト（{@link Node}）で共有し、要約は（先頭 node, 長さ）だけを持つ。
 */
public final class Diagnostics {
    /** enterRule ごとに 1 つ。親リンクだけを持ち、後から変更しない。 */
    private static final class Node {
        final int ruleId;
        final Node parent;
        Node(int ruleId, Node parent) { this.ruleId = ruleId; this.parent = parent; }
    }

    /** 配列を外部へ共有しない immutable な局所診断。stack は保存 rule からの suffix。 */
    public static final class Snapshot {
        private final int farthest, reached;
        private final int consumed, matched;
        private final Node stackTop;
        private final int stackLength;
        private final int[] expected;

        private Snapshot(int farthest, int reached, int consumed, int matched, Node stackTop, int stackLength, int[] expected) {
            this.farthest = farthest; this.reached = reached; this.consumed = consumed; this.matched = matched;
            this.stackTop = stackTop; this.stackLength = stackLength; this.expected = expected;
        }

        public int farthest() {
            return farthest;
        }

        public int consumed() { return consumed; }
        public int matched() { return matched; }

        public int[] expected() {
            return expected.clone();
        }

        private int[] ids() {
            return expected;
        }

        public int deepestRule() {
            return stackLength == 0 ? -1 : stackTop.ruleId;
        }

        public int[] ruleStack() {
            return materialize(stackTop, stackLength);
        }
    }

    private static final int[] NO_IDS = new int[0];

    private static int[] materialize(Node top, int length) {
        int[] stack = new int[length];
        Node node = top;
        for (int i = length - 1; i >= 0; i--) { stack[i] = node.ruleId; node = node.parent; }
        return stack;
    }

    /** 解析全体の集約。expected の重複除去は id ごとの世代 stamp。 */
    private static final class Global {
        int farthest = -1, reached = -1;
        int consumed, matched;
        int[] expected = new int[16];
        int expectedSize;
        Node stackTop;
        int stackLength;
        private int[] stamp;
        private int generation = 1;
        Global(int expectedIdHint) { stamp = new int[Math.max(16, expectedIdHint)]; }

        private void nextGeneration() {
            if (++generation == Integer.MAX_VALUE) { Arrays.fill(stamp, 0); generation = 1; }
        }

        private boolean mark(int expectedId) {
            if (expectedId >= stamp.length) stamp = Arrays.copyOf(stamp, Math.max(expectedId + 1, stamp.length * 2));
            if (stamp[expectedId] == generation) return false;
            stamp[expectedId] = generation;
            return true;
        }

        void fail(int pos, int expectedId, Node top, int length, int c, int m) {
            if (pos < farthest) {
                return;
            }
            if (pos > farthest) {
                farthest = pos; consumed = c; matched = m;
                expectedSize = 0; nextGeneration();
                stackTop = top; stackLength = length;
            } else if (length > stackLength) {
                stackTop = top; stackLength = length;
            }
            if (mark(expectedId)) {
                if (expectedSize == expected.length) expected = Arrays.copyOf(expected, expected.length * 2);
                expected[expectedSize++] = expectedId;
            }
        }

        Snapshot snapshot() {
            return new Snapshot(farthest, reached, consumed, matched, stackTop, stackLength, Arrays.copyOf(expected, expectedSize));
        }

        void restore(Snapshot snapshot) {
            farthest = snapshot.farthest; reached = snapshot.reached; consumed = snapshot.consumed; matched = snapshot.matched;
            stackTop = snapshot.stackTop; stackLength = snapshot.stackLength;
            int[] ids = snapshot.ids();
            expected = Arrays.copyOf(ids, Math.max(16, ids.length)); expectedSize = ids.length;
            nextGeneration();
            for (int id : ids) mark(id);
        }
    }

    /** A speculative parse may discard diagnostics only after its outcome is known. */
    public static final class Checkpoint {
        private final Snapshot global;
        private final int frames;
        private final int[] farthest, reached, consumed, matched, stackLength, expectedStart;
        private final Node[] stackTop;
        private final int[] expected;
        private Checkpoint(Diagnostics d) {
            global = d.global.snapshot();
            frames = d.frameCount;
            farthest = Arrays.copyOf(d.frameFarthest, frames); reached = Arrays.copyOf(d.frameReached, frames);
            consumed = Arrays.copyOf(d.frameConsumed, frames); matched = Arrays.copyOf(d.frameMatched, frames);
            stackLength = Arrays.copyOf(d.frameStackLength, frames); stackTop = Arrays.copyOf(d.frameStackTop, frames);
            expectedStart = Arrays.copyOf(d.frameExpectedStart, frames);
            expected = Arrays.copyOf(d.expectedStack, d.expectedTop);
        }
    }

    public Checkpoint checkpoint() { return new Checkpoint(this); }

    public void restore(Checkpoint checkpoint) {
        if (frameCount != checkpoint.frames) throw new IllegalStateException("Unbalanced diagnostic frames");
        global.restore(checkpoint.global);
        System.arraycopy(checkpoint.farthest, 0, frameFarthest, 0, frameCount); System.arraycopy(checkpoint.reached, 0, frameReached, 0, frameCount);
        System.arraycopy(checkpoint.consumed, 0, frameConsumed, 0, frameCount); System.arraycopy(checkpoint.matched, 0, frameMatched, 0, frameCount);
        System.arraycopy(checkpoint.stackLength, 0, frameStackLength, 0, frameCount); System.arraycopy(checkpoint.stackTop, 0, frameStackTop, 0, frameCount);
        System.arraycopy(checkpoint.expectedStart, 0, frameExpectedStart, 0, frameCount);
        if (checkpoint.expected.length > expectedStack.length) expectedStack = new int[checkpoint.expected.length];
        System.arraycopy(checkpoint.expected, 0, expectedStack, 0, checkpoint.expected.length);
        expectedTop = checkpoint.expected.length;
    }

    /** Expression memo needs a local accumulator without inventing a rule in the stack. */
    public void enterExpression() { push(-1); }

    private final Global global;
    // frame は深さで並ぶ並列配列。要約と expected 区間の開始位置だけを持つ。
    private int[] frameRule = new int[32], frameBase = new int[32], frameExpectedStart = new int[32];
    private int[] frameFarthest = new int[32], frameReached = new int[32], frameConsumed = new int[32], frameMatched = new int[32], frameStackLength = new int[32];
    private Node[] frameStackTop = new Node[32];
    private int frameCount;
    // frame ごとの expected 区間を積む共有 stack。区間の長さは最遠位置の候補数（数件）。
    private int[] expectedStack;
    private int expectedTop;
    private Node top;
    private int ruleCount;
    private int suppressionDepth;

    public Diagnostics() { this(64); }

    /** expectedIdHint は想定される expected id の上限（stamp 配列の初期長）。 */
    public Diagnostics(int expectedIdHint) { this(expectedIdHint, 256); }

    /** stackHint は expected 区間 stack の初期長（深さ × 最遠位置の候補数の目安）。 */
    public Diagnostics(int expectedIdHint, int stackHint) {
        global = new Global(expectedIdHint);
        expectedStack = new int[Math.max(16, stackHint)];
    }

    private void push(int ruleId) {
        if (frameCount == frameRule.length) {
            int n = frameCount * 2;
            frameRule = Arrays.copyOf(frameRule, n); frameBase = Arrays.copyOf(frameBase, n); frameExpectedStart = Arrays.copyOf(frameExpectedStart, n);
            frameFarthest = Arrays.copyOf(frameFarthest, n); frameReached = Arrays.copyOf(frameReached, n); frameConsumed = Arrays.copyOf(frameConsumed, n);
            frameMatched = Arrays.copyOf(frameMatched, n); frameStackLength = Arrays.copyOf(frameStackLength, n); frameStackTop = Arrays.copyOf(frameStackTop, n);
        }
        int i = frameCount++;
        frameRule[i] = ruleId; frameBase[i] = ruleCount; frameExpectedStart[i] = expectedTop;
        frameFarthest[i] = -1; frameReached[i] = -1; frameConsumed[i] = 0; frameMatched[i] = 0; frameStackLength[i] = 0; frameStackTop[i] = null;
    }

    /** 最内 frame の区間へ 1 件追加する（既にあれば何もしない）。 */
    private void addExpected(int start, int expectedId) {
        for (int k = start; k < expectedTop; k++) if (expectedStack[k] == expectedId) return;
        if (expectedTop == expectedStack.length) expectedStack = Arrays.copyOf(expectedStack, expectedTop * 2);
        expectedStack[expectedTop++] = expectedId;
    }

    /** 最内 frame の要約と expected 区間へ 1 件の失敗を記録する（Global.fail と同じ規則）。 */
    private void failTop(int pos, int expectedId, Node node, int length, int c, int m) {
        int i = frameCount - 1;
        int farthest = frameFarthest[i];
        if (pos < farthest) return;
        int start = frameExpectedStart[i];
        if (pos > farthest) {
            frameFarthest[i] = pos; frameConsumed[i] = c; frameMatched[i] = m; frameStackTop[i] = node; frameStackLength[i] = length;
            expectedTop = start;
        } else if (length > frameStackLength[i]) {
            frameStackTop[i] = node; frameStackLength[i] = length;
        }
        addExpected(start, expectedId);
    }

    public int farthest() {
        return global.farthest;
    }

    /** 最内 frame の最遠失敗位置。frame 外では global。snapshot() を作らずに読む。 */
    public int localFarthest() {
        return frameCount == 0 ? global.farthest : frameFarthest[frameCount - 1];
    }

    public int reached() { return global.reached; }

    public void reach(int position) {
        if (suppressionDepth != 0) return;
        if (position > global.reached) global.reached = position;
        if (frameCount > 0 && position > frameReached[frameCount - 1]) frameReached[frameCount - 1] = position;
    }

    public int[] expected() {
        return Arrays.copyOf(global.expected, global.expectedSize);
    }

    public int deepestRule() {
        return global.stackLength == 0 ? -1 : global.stackTop.ruleId;
    }

    public int[] ruleStack() {
        return materialize(global.stackTop, global.stackLength);
    }

    public void enterRule(int ruleId) {
        if (ruleId < 0) {
            throw new IllegalArgumentException("Negative rule id");
        }
        push(ruleId);
        top = new Node(ruleId, top); ruleCount++;
    }

    /** 成功・失敗のどちらでも同じ終了操作。戻り値を memo payload に保存できる。 */
    public Snapshot leaveRule() { return leaveRule(true); }

    /** snapshot=false は payload を保存しない呼出し向け（null を返す）。親への吸収は同じ。 */
    public Snapshot leaveRule(boolean snapshot) {
        if (frameCount == 0) {
            throw new IllegalStateException("No active rule");
        }
        int leaving = --frameCount;
        if (frameRule[leaving] >= 0) { top = top.parent; ruleCount--; }
        int childStart = frameExpectedStart[leaving];
        Snapshot result = snapshot ? new Snapshot(frameFarthest[leaving], frameReached[leaving], frameConsumed[leaving], frameMatched[leaving],
            frameStackTop[leaving], frameStackLength[leaving],
            expectedTop == childStart ? NO_IDS : Arrays.copyOfRange(expectedStack, childStart, expectedTop)) : null;
        int absorbed = childStart;
        if (frameCount > 0) {
            int parent = frameCount - 1;
            if (frameReached[leaving] > frameReached[parent]) frameReached[parent] = frameReached[leaving];
            int farthest = frameFarthest[leaving];
            if (farthest >= 0 && farthest >= frameFarthest[parent]) {
                int length = frameStackLength[leaving] + frameBase[leaving] - frameBase[parent];
                int parentStart = frameExpectedStart[parent];
                if (farthest > frameFarthest[parent]) {
                    frameFarthest[parent] = farthest; frameConsumed[parent] = frameConsumed[leaving]; frameMatched[parent] = frameMatched[leaving];
                    frameStackTop[parent] = frameStackTop[leaving]; frameStackLength[parent] = length;
                    // 親の区間を子の区間で置き換える（子の区間は親の直後にあるので前へ詰める）。
                    int size = expectedTop - childStart;
                    System.arraycopy(expectedStack, childStart, expectedStack, parentStart, size);
                    absorbed = parentStart + size;
                } else {
                    if (length > frameStackLength[parent]) {
                        frameStackTop[parent] = frameStackTop[leaving]; frameStackLength[parent] = length;
                    }
                    // 同じ位置: 親に無い子の id だけを親の区間の末尾へ詰める（書込位置は常に読出位置以下）。
                    int write = childStart;
                    for (int k = childStart; k < expectedTop; k++) {
                        int id = expectedStack[k];
                        boolean seen = false;
                        for (int j = parentStart; j < write; j++) if (expectedStack[j] == id) { seen = true; break; }
                        if (!seen) expectedStack[write++] = id;
                    }
                    absorbed = write;
                }
            }
        }
        expectedTop = absorbed;
        return result;
    }

    /** pos は呼出し側が選んだ一貫した位置単位。UTF-16 / code point 変換は行わない。 */
    public void failAt(int pos, int expectedId) { failAt(pos, expectedId, pos, pos); }

    public void failureCursor(int pos, int consumed, int matched) {
        if (suppressionDepth != 0) return;
        if (global.farthest == pos) { global.consumed = consumed; global.matched = matched; }
        for (int i = 0; i < frameCount; i++) if (frameFarthest[i] == pos) { frameConsumed[i] = consumed; frameMatched[i] = matched; }
    }

    public void failAt(int pos, int expectedId, int consumed, int matched) {
        if (pos < 0 || expectedId < 0) {
            throw new IllegalArgumentException("Negative failure position or expected id");
        }
        if (suppressionDepth != 0) {
            return;
        }
        global.fail(pos, expectedId, top, ruleCount, consumed, matched);
        if (frameCount > 0) {
            failTop(pos, expectedId, top, ruleCount - frameBase[frameCount - 1], consumed, matched);
        }
    }

    /**
     * 同じ位置へ複数の label をまとめて記録する（候補除外の静的再生）。{@code suffix} は「その候補が
     * 入っていたはずの rule 列」（外側から内側）で、現在の stack の上に付け足して記録する。
     * 観測は「候補を実際に評価して同じ位置で同じ順に記録した」場合と等しい: expected は記録順・
     * 重複除去、rule 経路は最大長のうち最初のもの（{@code length > stackLength} でのみ更新）、
     * consumed / matched は最遠位置を更新した記録のものだけが残る。
     */
    public void failBatch(int pos, int[] expectedIds, int[] suffix, int consumed, int matched) {
        if (pos < 0) {
            throw new IllegalArgumentException("Negative failure position");
        }
        if (suppressionDepth != 0 || expectedIds.length == 0) {
            return;
        }
        boolean local = frameCount > 0;
        if (pos < global.farthest && (!local || pos < frameFarthest[frameCount - 1])) {
            return;
        }
        Node rebased = top;
        for (int ruleId : suffix) rebased = new Node(ruleId, rebased);
        int length = ruleCount + suffix.length;
        for (int id : expectedIds) global.fail(pos, id, rebased, length, consumed, matched);
        if (local) {
            failTop(pos, expectedIds[0], rebased, ruleCount - frameBase[frameCount - 1] + suffix.length, consumed, matched);
            int i = frameCount - 1;
            if (frameFarthest[i] == pos) {
                int start = frameExpectedStart[i];
                for (int k = 1; k < expectedIds.length; k++) addExpected(start, expectedIds[k]);
            }
        }
    }

    public void suppressBegin() {
        suppressionDepth = Math.incrementExact(suppressionDepth);
    }

    public void suppressEnd() {
        if (suppressionDepth == 0) {
            throw new IllegalStateException("No diagnostic suppression to end");
        }
        suppressionDepth--;
    }

    /** rule 内では最内 rule の局所結果、rule 外では解析全体の結果を返す。 */
    public Snapshot snapshot() {
        if (frameCount == 0) return global.snapshot();
        int i = frameCount - 1;
        int start = frameExpectedStart[i];
        return new Snapshot(frameFarthest[i], frameReached[i], frameConsumed[i], frameMatched[i], frameStackTop[i], frameStackLength[i],
            expectedTop == start ? NO_IDS : Arrays.copyOfRange(expectedStack, start, expectedTop));
    }

    /** memo hit 時、保存 rule を enter する前に呼ぶ。呼出元の stack を付け替える。 */
    public void merge(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (suppressionDepth != 0) {
            return;
        }
        reach(snapshot.reached);
        if (snapshot.farthest < 0) return;
        boolean local = frameCount > 0;
        if (snapshot.farthest < global.farthest && (!local || snapshot.farthest < frameFarthest[frameCount - 1])) return;
        int[] ids = snapshot.ids();
        if (ids.length == 0) return;
        // 保存 stack を現在の stack の上に付け替える。
        Node rebased = top;
        int[] saved = materialize(snapshot.stackTop, snapshot.stackLength);
        for (int ruleId : saved) rebased = new Node(ruleId, rebased);
        int length = ruleCount + snapshot.stackLength;
        for (int id : ids) global.fail(snapshot.farthest, id, rebased, length, snapshot.consumed, snapshot.matched);
        if (local) {
            int localLength = ruleCount - frameBase[frameCount - 1] + snapshot.stackLength;
            failTop(snapshot.farthest, ids[0], rebased, localLength, snapshot.consumed, snapshot.matched);
            int i = frameCount - 1;
            if (frameFarthest[i] == snapshot.farthest) {
                int start = frameExpectedStart[i];
                for (int k = 1; k < ids.length; k++) addExpected(start, ids[k]);
            }
        }
    }
}
