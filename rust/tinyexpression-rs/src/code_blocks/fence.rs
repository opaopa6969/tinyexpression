//! Atomic extended code-block recognition. Offsets are UTF-8 byte boundaries.
//! Four or more backticks open a block; exactly the same width on its own line
//! closes it. The body is opaque, including strings, comments and shorter fences.

pub(crate) struct Layout {
    pub body_start: usize,
    pub body_end: usize,
    pub fence_end: usize,
    pub end: usize,
}

pub(crate) fn scan(source: &str, start: usize) -> Option<Layout> {
    let bytes = source.as_bytes();
    if start >= bytes.len() || (start > 0 && !eol(bytes[start - 1])) {
        return None;
    }
    let mut p = start;
    while bytes.get(p) == Some(&b'`') {
        p += 1;
    }
    let width = p - start;
    if width < 4 {
        return None;
    }
    p = identifier_end(bytes, p)?;
    if bytes.get(p) != Some(&b':') {
        return None;
    }
    p = identifier_end(bytes, p + 1)?;
    while bytes.get(p) == Some(&b'.') {
        p = identifier_end(bytes, p + 1)?;
    }
    if !bytes.get(p).is_some_and(|c| eol(*c)) {
        return None;
    }
    let body_start = after_line(bytes, p);
    let body_end = closing_line(bytes, body_start, width)?;
    let fence_end = body_end + width;
    Some(Layout {
        body_start,
        body_end,
        fence_end,
        end: after_line(bytes, fence_end),
    })
}

/// Only for layout-preserving masking; malformed input is still rejected by scan.
pub(crate) fn opaque_end(source: &str) -> usize {
    let bytes = source.as_bytes();
    let width = bytes.iter().take_while(|&&c| c == b'`').count();
    closing_line(bytes, width, width).map_or(bytes.len(), |p| p + width)
}

fn closing_line(bytes: &[u8], from: usize, width: usize) -> Option<usize> {
    let mut p = from;
    while p < bytes.len() {
        if (p == 0 || eol(bytes[p - 1])) && bytes[p] == b'`' {
            let start = p;
            while bytes.get(p) == Some(&b'`') {
                p += 1;
            }
            if p - start == width && bytes.get(p).is_none_or(|c| eol(*c)) {
                return Some(start);
            }
        } else {
            p += 1;
        }
    }
    None
}

fn identifier_end(bytes: &[u8], from: usize) -> Option<usize> {
    if !bytes.get(from).is_some_and(|c| head(*c)) {
        return None;
    }
    let mut p = from + 1;
    while bytes.get(p).is_some_and(|c| head(*c) || c.is_ascii_digit()) {
        p += 1;
    }
    Some(p)
}
fn head(c: u8) -> bool {
    c.is_ascii_alphabetic() || c == b'_'
}
fn eol(c: u8) -> bool {
    matches!(c, b'\r' | b'\n')
}
fn after_line(bytes: &[u8], mut p: usize) -> usize {
    if bytes.get(p) == Some(&b'\r') {
        p += 1;
    }
    if bytes.get(p) == Some(&b'\n') {
        p += 1;
    }
    p
}
