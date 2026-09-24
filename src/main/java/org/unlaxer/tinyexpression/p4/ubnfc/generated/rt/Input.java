// ubnfc runtime template: Input
package org.unlaxer.tinyexpression.p4.ubnfc.generated.rt;

import java.util.Arrays;
import java.util.Objects;

/** 入力内部は UTF-16、公開 span は code point。解析セッションごとに一つ作る。 */
public final class Input {
    private final String source;
    private final char[] chars;
    private final int[] boundaries;

    public Input(String source) {
        this.source = Objects.requireNonNull(source);
        chars = source.toCharArray();
        int count = Character.codePointCount(chars, 0, chars.length);
        if (count == chars.length) {
            boundaries = null;
        } else {
            boundaries = new int[count + 1];
            int utf16 = 0;
            for (int cp = 0; cp < count; cp++) {
                boundaries[cp] = utf16;
                utf16 += Character.charCount(Character.codePointAt(chars, utf16));
            }
            boundaries[count] = chars.length;
        }
    }

    public int length() {
        return chars.length;
    }

    /** UTF-16 単位の 1 文字。サロゲートはそのまま返す（接頭辞の先頭判定用）。 */
    public char charAt(int idx) {
        return chars[idx];
    }

    public int codePointAt(int idx) {
        char c = chars[idx];
        if (Character.isHighSurrogate(c) && idx + 1 < chars.length) {
            char d = chars[idx + 1];
            if (Character.isLowSurrogate(d)) return Character.toCodePoint(c, d);
        }
        return c;
    }

    /** 単語境界を要求しない接頭辞一致。入力末尾では空リテラルだけが一致する。 */
    public boolean startsWith(int idx, String lit) {
        Objects.requireNonNull(lit);
        if (idx < 0 || idx > chars.length - lit.length()) {
            return false;
        }
        for (int i = 0; i < lit.length(); i++) {
            if (chars[idx + i] != lit.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** 補助平面の大小文字・孤立サロゲートも JDK の規則と完全に一致させる。 */
    public boolean startsWithIgnoreCase(int idx, String lit) {
        Objects.requireNonNull(lit);
        return source.regionMatches(true, idx, lit, 0, lit.length());
    }

    /** 両端は code point 境界であること。サロゲートペア内部は拒否する。 */
    public int cpLength(int start, int end) {
        Objects.checkFromToIndex(start, end, chars.length);
        return cpIndex(end) - cpIndex(start);
    }

    /** UTF-16 境界から code point index へ。末尾を含み、ペア内部は拒否する。 */
    public int cpIndex(int utf16) {
        if (utf16 < 0 || utf16 > chars.length) {
            throw new IndexOutOfBoundsException(utf16);
        }
        if (boundaries == null) {
            return utf16;
        }
        int cp = Arrays.binarySearch(boundaries, utf16);
        if (cp < 0) {
            throw new IllegalArgumentException("UTF-16 index is inside a surrogate pair: " + utf16);
        }
        return cp;
    }

    public int utf16Index(int cp) {
        int length = boundaries == null ? chars.length : boundaries.length - 1;
        if (cp < 0 || cp > length) {
            throw new IndexOutOfBoundsException(cp);
        }
        return boundaries == null ? cp : boundaries[cp];
    }
}
