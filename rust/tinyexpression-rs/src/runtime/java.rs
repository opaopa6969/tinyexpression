//! Java library behaviour the evaluator depends on, reproduced bit for bit where Java defines it.
//!
//! Everything here is a pure function of its inputs: number spelling (`Float.toString`,
//! `Double.toString`), number parsing (`Float.parseFloat`, `Integer.parseInt`, ...),
//! `Character.isWhitespace`/`String.strip`/`String.trim`, UTF-16 lengths and `java.lang.Math`
//! special cases that differ from Rust's `f64` methods.

/// `Character.isWhitespace`: Unicode space separators except the no-break ones, plus the ASCII
/// controls `\t \n \u{b} \u{c} \r` and the information separators `\u{1c}..=\u{1f}`.
pub(crate) fn is_whitespace(c: char) -> bool {
    match c {
        '\t' | '\n' | '\u{b}' | '\u{c}' | '\r' | '\u{1c}'..='\u{1f}' | ' ' => true,
        '\u{a0}' | '\u{2007}' | '\u{202f}' => false,
        '\u{1680}'
        | '\u{2000}'..='\u{200a}'
        | '\u{2028}'
        | '\u{2029}'
        | '\u{205f}'
        | '\u{3000}' => true,
        _ => false,
    }
}

/// `String.strip()`.
pub(crate) fn strip(s: &str) -> &str {
    s.trim_matches(is_whitespace)
}

/// `String.trim()`: removes code units `<= ' '` from both ends.
pub(crate) fn trim(s: &str) -> &str {
    s.trim_matches(|c: char| c <= ' ')
}

/// `String.length()` in UTF-16 code units.
pub(crate) fn utf16_len(s: &str) -> usize {
    s.chars().map(char::len_utf16).sum()
}

/// `Float.toString` (JDK 19+ shortest-decimal algorithm, at least two significant digits).
pub fn float_to_string(value: f32) -> String {
    if value.is_nan() {
        return "NaN".to_owned();
    }
    if value.is_infinite() {
        return if value > 0.0 { "Infinity" } else { "-Infinity" }.to_owned();
    }
    if value == 0.0 {
        return if value.is_sign_negative() {
            "-0.0"
        } else {
            "0.0"
        }
        .to_owned();
    }
    let absolute = value.abs();
    let (digits, exponent) = (2..=9)
        .find_map(|significant| {
            let scientific = format!("{:.*e}", significant - 1, f64::from(absolute));
            let parsed = scientific.parse::<f32>().ok()?;
            (parsed.to_bits() == absolute.to_bits()).then(|| split_scientific(&scientific))
        })
        .expect("every finite f32 has a round-tripping decimal with at most 9 digits");
    layout(
        value.is_sign_negative(),
        digits,
        exponent,
        (1.0e-3..1.0e7).contains(&absolute),
    )
}

/// `Double.toString` (JDK 19+ shortest-decimal algorithm, at least two significant digits).
pub fn double_to_string(value: f64) -> String {
    if value.is_nan() {
        return "NaN".to_owned();
    }
    if value.is_infinite() {
        return if value > 0.0 { "Infinity" } else { "-Infinity" }.to_owned();
    }
    if value == 0.0 {
        return if value.is_sign_negative() {
            "-0.0"
        } else {
            "0.0"
        }
        .to_owned();
    }
    let absolute = value.abs();
    let (digits, exponent) = (2..=17)
        .find_map(|significant| {
            let scientific = format!("{:.*e}", significant - 1, absolute);
            let parsed = scientific.parse::<f64>().ok()?;
            (parsed.to_bits() == absolute.to_bits()).then(|| split_scientific(&scientific))
        })
        .expect("every finite f64 has a round-tripping decimal with at most 17 digits");
    layout(
        value.is_sign_negative(),
        digits,
        exponent,
        (1.0e-3..1.0e7).contains(&absolute),
    )
}

fn split_scientific(scientific: &str) -> (String, i32) {
    let (mantissa, exponent) = scientific
        .split_once('e')
        .expect("`{:e}` output has an exponent");
    let mut digits = mantissa.replace('.', "");
    while digits.len() > 2 && digits.ends_with('0') {
        digits.pop();
    }
    (
        digits,
        exponent.parse().expect("`{:e}` exponent is an integer"),
    )
}

fn layout(negative: bool, digits: String, exponent: i32, plain_range: bool) -> String {
    let sign = if negative { "-" } else { "" };
    if !plain_range {
        let (first, rest) = digits.split_at(1);
        let mut rest = rest.trim_end_matches('0').to_owned();
        if rest.is_empty() {
            rest.push('0');
        }
        return format!("{sign}{first}.{rest}E{exponent}");
    }
    let decimal_position = exponent + 1;
    let mut plain = if decimal_position <= 0 {
        format!("0.{}{}", "0".repeat((-decimal_position) as usize), digits)
    } else if decimal_position as usize >= digits.len() {
        format!(
            "{}{}.0",
            digits,
            "0".repeat(decimal_position as usize - digits.len())
        )
    } else {
        let (integer, fraction) = digits.split_at(decimal_position as usize);
        format!("{integer}.{fraction}")
    };
    while plain.ends_with('0') {
        plain.pop();
    }
    if plain.ends_with('.') {
        plain.push('0');
    }
    format!("{sign}{plain}")
}

/// Validates the `FloatingDecimal` grammar shared by `Float.parseFloat` and
/// `Double.parseDouble` and returns the text Rust's parser accepts for the same value.
fn java_decimal(input: &str) -> Option<String> {
    let s = trim(input);
    let (sign, body) = match s.as_bytes().first() {
        Some(b'+') => ("", &s[1..]),
        Some(b'-') => ("-", &s[1..]),
        _ => ("", s),
    };
    if body == "NaN" {
        return Some("NaN".to_owned());
    }
    if body == "Infinity" {
        return Some(format!("{sign}inf"));
    }
    let body = body.strip_suffix(['f', 'F', 'd', 'D']).unwrap_or(body);
    let bytes = body.as_bytes();
    let mut index = 0;
    let integer_start = index;
    while index < bytes.len() && bytes[index].is_ascii_digit() {
        index += 1;
    }
    let integer_digits = index - integer_start;
    let mut fraction_digits = 0;
    if index < bytes.len() && bytes[index] == b'.' {
        index += 1;
        let fraction_start = index;
        while index < bytes.len() && bytes[index].is_ascii_digit() {
            index += 1;
        }
        fraction_digits = index - fraction_start;
    }
    if integer_digits + fraction_digits == 0 {
        return None;
    }
    if index < bytes.len() && matches!(bytes[index], b'e' | b'E') {
        index += 1;
        if index < bytes.len() && matches!(bytes[index], b'+' | b'-') {
            index += 1;
        }
        let exponent_start = index;
        while index < bytes.len() && bytes[index].is_ascii_digit() {
            index += 1;
        }
        if index == exponent_start {
            return None;
        }
    }
    (index == bytes.len()).then(|| format!("{sign}{body}"))
}

/// `Float.parseFloat`. Hexadecimal floating literals are not accepted (documented deviation).
pub fn parse_float(input: &str) -> Option<f32> {
    java_decimal(input)?.parse().ok()
}

/// `Double.parseDouble`. Hexadecimal floating literals are not accepted (documented deviation).
pub fn parse_double(input: &str) -> Option<f64> {
    java_decimal(input)?.parse().ok()
}

/// `Long.parseLong` restricted to the given range (`Integer.parseInt`, `Short.parseShort`, ...).
/// Java also accepts non-ASCII decimal digits; so does this (via `char::to_digit` on Unicode
/// `Nd` is not available in std, so only ASCII digits are accepted — documented deviation).
pub fn parse_integer(input: &str, min: i64, max: i64) -> Option<i64> {
    let (negative, digits) = match input.as_bytes().first() {
        Some(b'+') => (false, &input[1..]),
        Some(b'-') => (true, &input[1..]),
        _ => (false, input),
    };
    if digits.is_empty() || !digits.bytes().all(|b| b.is_ascii_digit()) {
        return None;
    }
    let mut value: i128 = 0;
    for b in digits.bytes() {
        value = value * 10 + i128::from(b - b'0');
        if value > i128::from(i64::MAX) + 1 {
            return None;
        }
    }
    let value = if negative { -value } else { value };
    (i128::from(min)..=i128::from(max))
        .contains(&value)
        .then_some(value as i64)
}

/// `Math.min(double, double)`: NaN wins, `-0.0 < 0.0`.
pub(crate) fn math_min(a: f64, b: f64) -> f64 {
    if a.is_nan() {
        return a;
    }
    if b.is_nan() {
        return b;
    }
    if a == 0.0 && b == 0.0 {
        return if a.is_sign_negative() { a } else { b };
    }
    if a <= b {
        a
    } else {
        b
    }
}

/// `Math.max(double, double)`: NaN wins, `0.0 > -0.0`.
pub(crate) fn math_max(a: f64, b: f64) -> f64 {
    if a.is_nan() {
        return a;
    }
    if b.is_nan() {
        return b;
    }
    if a == 0.0 && b == 0.0 {
        return if a.is_sign_negative() { b } else { a };
    }
    if a >= b {
        a
    } else {
        b
    }
}

/// `Math.round(double)` (returns `long`): `floor(x + 1/2)` computed exactly, NaN -> 0,
/// saturating at the `long` range.
pub(crate) fn math_round(a: f64) -> i64 {
    if a.is_nan() {
        return 0;
    }
    let floor = a.floor();
    let fraction = a - floor;
    let rounded = if fraction >= 0.5 { floor + 1.0 } else { floor };
    rounded as i64
}

/// `Math.pow`: the cases where Java's specification differs from C `pow`.
pub(crate) fn math_pow(base: f64, exponent: f64) -> f64 {
    if exponent.is_nan() {
        return if exponent == 0.0 { 1.0 } else { f64::NAN };
    }
    if exponent == 0.0 {
        return 1.0;
    }
    if base.is_nan() {
        return f64::NAN;
    }
    if base.abs() == 1.0 && exponent.is_infinite() {
        return f64::NAN;
    }
    base.powf(exponent)
}

/// `Math.toRadians`.
pub(crate) fn to_radians(degrees: f64) -> f64 {
    degrees * 0.017_453_292_519_943_295
}

/// `Double.compare`.
pub(crate) fn double_compare(a: f64, b: f64) -> std::cmp::Ordering {
    if a < b {
        return std::cmp::Ordering::Less;
    }
    if a > b {
        return std::cmp::Ordering::Greater;
    }
    let canonical = |v: f64| {
        if v.is_nan() {
            0x7ff8_0000_0000_0000_i64
        } else {
            v.to_bits() as i64
        }
    };
    canonical(a).cmp(&canonical(b))
}

/// `Float.compare`.
pub(crate) fn float_compare(a: f32, b: f32) -> std::cmp::Ordering {
    if a < b {
        return std::cmp::Ordering::Less;
    }
    if a > b {
        return std::cmp::Ordering::Greater;
    }
    let canonical = |v: f32| {
        if v.is_nan() {
            0x7fc0_0000_i32
        } else {
            v.to_bits() as i32
        }
    };
    canonical(a).cmp(&canonical(b))
}

/// `String.toUpperCase()` in a non-Turkic default locale.
pub(crate) fn to_upper(s: &str) -> String {
    s.to_uppercase()
}

/// `String.toLowerCase()` in a non-Turkic default locale.
pub(crate) fn to_lower(s: &str) -> String {
    s.to_lowercase()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn float_spelling_matches_java() {
        for (value, text) in [
            (1.0f32, "1.0"),
            (0.1, "0.1"),
            (1.0e7, "1.0E7"),
            (1.0e-3, "0.001"),
            (1.0e-4, "1.0E-4"),
            (f32::MIN_POSITIVE, "1.1754944E-38"),
            (1.4e-45, "1.4E-45"),
            (3.4028235e38, "3.4028235E38"),
            (123456.79, "123456.79"),
            (-0.0, "-0.0"),
        ] {
            assert_eq!(float_to_string(value), text);
        }
    }

    #[test]
    fn double_spelling_matches_java() {
        for (value, text) in [
            (1.0f64, "1.0"),
            (0.1, "0.1"),
            (1.0e7, "1.0E7"),
            (1.0e-3, "0.001"),
            (4.9e-324, "4.9E-324"),
            (1.7976931348623157e308, "1.7976931348623157E308"),
            (2.0 / 3.0, "0.6666666666666666"),
            (100.0, "100.0"),
        ] {
            assert_eq!(double_to_string(value), text);
        }
    }

    #[test]
    fn java_number_grammar() {
        assert_eq!(parse_float(" 1.5f "), Some(1.5));
        assert_eq!(parse_float("1e1"), Some(10.0));
        assert_eq!(parse_float(".5"), Some(0.5));
        assert_eq!(parse_float("5."), Some(5.0));
        assert!(parse_float("-Infinity").unwrap().is_infinite());
        assert!(parse_float("NaN").unwrap().is_nan());
        assert_eq!(parse_float("inf"), None);
        assert_eq!(parse_float("1e"), None);
        assert_eq!(parse_float(""), None);
        assert_eq!(
            parse_integer("-12", i32::MIN.into(), i32::MAX.into()),
            Some(-12)
        );
        assert_eq!(
            parse_integer("2147483648", i32::MIN.into(), i32::MAX.into()),
            None
        );
        assert_eq!(parse_integer(" 1", i32::MIN.into(), i32::MAX.into()), None);
    }

    #[test]
    fn math_special_cases() {
        assert!(math_pow(1.0, f64::NAN).is_nan());
        assert_eq!(math_round(-2.5), -2);
        assert_eq!(math_round(2.5), 3);
        assert_eq!(math_round(0.49999999999999994), 0);
        assert!(math_min(-0.0, 0.0).is_sign_negative());
        assert!(math_max(f64::NAN, 1.0).is_nan());
    }
}
