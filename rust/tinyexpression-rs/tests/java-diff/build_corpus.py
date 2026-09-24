#!/usr/bin/env python3
"""Collects every formula the repository evaluates in tests and pairs it with contexts.

Output: one JSON object per line with
  id, origin, formula, resultType, numberType, profile, vars, externals
`vars` is a list of [map, javaType, name, value] where map is number|string|boolean|object.
The same rows drive the Java golden (JavaDiffDriver) and the Rust differential test.
Deterministic: no randomness, stable ordering.
"""
import json
import re
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
MAX_LEN = 25000

JAVA_ESCAPES = {'n': '\n', 't': '\t', 'r': '\r', 'b': '\b', 'f': '\f', 's': ' ',
                '"': '"', "'": "'", '\\': '\\', '0': '\0'}


def unescape_java(body):
    out = []
    i = 0
    while i < len(body):
        c = body[i]
        if c == '\\' and i + 1 < len(body):
            n = body[i + 1]
            if n == 'u':
                j = i + 1
                while j < len(body) and body[j] == 'u':
                    j += 1
                code = body[j:j + 4]
                try:
                    out.append(chr(int(code, 16)))
                    i = j + 4
                    continue
                except ValueError:
                    pass
            if n in JAVA_ESCAPES:
                out.append(JAVA_ESCAPES[n])
                i += 2
                continue
        out.append(c)
        i += 1
    s = ''.join(out)
    # re-join UTF-16 surrogate pairs written as \\uD83D\\uDE00
    try:
        s = s.encode('utf-16', 'surrogatepass').decode('utf-16')
    except UnicodeError:
        return None
    return s


def text_block(body):
    lines = body.split('\n')
    if lines and lines[0].strip() == '':
        lines = lines[1:]
    indents = [len(l) - len(l.lstrip()) for l in lines if l.strip()]
    closing = lines[-1] if lines else ''
    if closing.strip() == '':
        indents.append(len(closing))
    cut = min(indents) if indents else 0
    stripped = [l[cut:].rstrip() for l in lines]
    text = '\n'.join(stripped)
    return unescape_java(text.replace('\\\n', ''))


def java_literals(source):
    out = []
    i = 0
    n = len(source)
    while i < n:
        c = source[i]
        if source.startswith('//', i):
            j = source.find('\n', i)
            i = n if j < 0 else j
            continue
        if source.startswith('/*', i):
            j = source.find('*/', i + 2)
            i = n if j < 0 else j + 2
            continue
        if c == "'":
            j = i + 1
            while j < n and source[j] != "'":
                j += 2 if source[j] == '\\' else 1
            i = j + 1
            continue
        if source.startswith('"""', i):
            j = source.find('"""', i + 3)
            while j > 0 and source[j - 1] == '\\':
                j = source.find('"""', j + 1)
            if j < 0:
                break
            value = text_block(source[i + 3:j])
            if value is not None:
                out.append(value)
            i = j + 3
            continue
        if c == '"':
            j = i + 1
            while j < n and source[j] != '"' and source[j] != '\n':
                j += 2 if source[j] == '\\' else 1
            value = unescape_java(source[i + 1:j])
            if value is not None:
                out.append(value)
            i = j + 1
            continue
        i += 1
    return out


RUST_ESCAPES = {'n': '\n', 't': '\t', 'r': '\r', '"': '"', "'": "'", '\\': '\\', '0': '\0'}


def rust_literals(source):
    out = []
    for m in re.finditer(r'r(#*)"(.*?)"\1', source, re.S):
        out.append(m.group(2))
    cleaned = re.sub(r'r(#*)".*?"\1', '""', source, flags=re.S)
    cleaned = re.sub(r"//[^\n]*", "", cleaned)
    for m in re.finditer(r'"((?:\\.|[^"\\])*)"', cleaned, re.S):
        body = m.group(1)
        body = re.sub(r'\\\n\s*', '', body)
        res = []
        i = 0
        while i < len(body):
            if body[i] == '\\' and i + 1 < len(body):
                nx = body[i + 1]
                if nx == 'u' and body[i + 2:i + 3] == '{':
                    k = body.index('}', i)
                    res.append(chr(int(body[i + 3:k], 16)))
                    i = k + 1
                    continue
                res.append(RUST_ESCAPES.get(nx, nx))
                i += 2
                continue
            res.append(body[i])
            i += 1
        out.append(''.join(res))
    return out


def plausible(formula):
    s = formula.strip()
    if not s or len(formula) > MAX_LEN:
        return False
    if '\x00' in formula:
        return False
    return True


def formula_info_blocks(text):
    """Minimal FormulaInfo reader: key:value lines, value continues until next key or END."""
    blocks = []
    for part in text.split('---END_OF_PART---'):
        entries = {}
        key = None
        for line in part.split('\n'):
            m = re.match(r'^([A-Za-z_][A-Za-z0-9_]*):(.*)$', line)
            if m and not line.startswith('#'):
                key = m.group(1)
                entries[key] = [m.group(2)]
            elif key is not None:
                entries[key].append(line)
        if 'formula' in entries:
            def value(k):
                lines = [l for l in entries.get(k, []) if l.strip() and not l.startswith('#')]
                return '\n'.join(lines).strip()
            blocks.append({
                'formula': '\n'.join(l for l in entries['formula'] if not l.startswith('#')).strip('\n'),
                'resultType': value('resultType'),
                'numberType': value('numberType'),
                'name': value('calculatorName') or value('var') or value('checkKind'),
            })
    return blocks


TYPE_ALIASES = {
    'float': 'float', 'java.lang.float': 'float', 'number': 'float',
    'double': 'double', 'java.lang.double': 'double',
    'int': 'int', 'integer': 'int', 'java.lang.integer': 'int',
    'long': 'long', 'java.lang.long': 'long',
    'short': 'short', 'java.lang.short': 'short',
    'byte': 'byte', 'java.lang.byte': 'byte',
    'string': 'string', 'java.lang.string': 'string',
    'boolean': 'boolean', 'java.lang.boolean': 'boolean',
    'object': 'object', 'java.lang.object': 'object',
}


def norm_type(t):
    if not t:
        return None
    return TYPE_ALIASES.get(t.strip().lower())


# ---------------------------------------------------------------- contexts
def h(name, salt):
    return zlib.crc32(f'{salt}:{name}'.encode())


NUM_POOL = ['0', '1', '2', '3', '5', '10', '18', '100', '0.5', '0.1', '-1.25', '12', '3.5', '2.5']
STR_POOL = ['jp', 'opa', 'hello', '  hello  ', '123', 'true', '3.5', '😀a', '', 'Dr. Who', 'ios', 'Safari/17']
VAR_RE = re.compile(r'\$([A-Za-z_][A-Za-z0-9_]*)')
STR_LIT_RE = re.compile(r"'((?:\\.|[^'\\])*)'|\"((?:\\.|[^\"\\])*)\"")


def num_for(name):
    if name == 'nowHour':
        return '12'
    if name == 'nowDayOfWeek':
        return '3'
    return NUM_POOL[h(name, 'num') % len(NUM_POOL)]


def str_for(name, formula):
    lits = [a or b for a, b in STR_LIT_RE.findall(formula)]
    lits = [l for l in lits if '\\' not in l and len(l) < 40]
    pool = lits + STR_POOL
    return pool[h(name, 'str') % len(pool)]


def bool_for(name):
    return 'true' if h(name, 'bool') % 2 == 0 else 'false'


def test_context_values():
    """Aggregates literal `.set("x", v)` / `.setObject("x", v)` calls from the Java tests."""
    values = {}
    pattern = re.compile(r'\.(set|setObject)\("([A-Za-z_][A-Za-z0-9_]*)"\s*,\s*([^;\n]{1,60}?)\)\s*;')
    for path in sorted((ROOT / 'src' / 'test').rglob('*.java')):
        for method, name, raw in pattern.findall(path.read_text(encoding='utf-8', errors='replace')):
            raw = raw.strip()
            entry = None
            if re.fullmatch(r'"(?:\\.|[^"\\])*"', raw):
                entry = ('object' if method == 'setObject' else 'string', 'string', unescape_java(raw[1:-1]))
            elif raw in ('true', 'false'):
                entry = ('object' if method == 'setObject' else 'boolean', 'boolean', raw)
            elif re.fullmatch(r'-?\d+(\.\d+)?[fF]', raw) or re.fullmatch(r'-?\d+\.\d+', raw):
                entry = ('object' if method == 'setObject' else 'number',
                         'float' if raw[-1] in 'fF' or method == 'set' else 'double',
                         raw.rstrip('fF'))
            elif re.fullmatch(r'-?\d+', raw):
                # set(String, float) wins over set(String, Number) for an int literal.
                entry = ('object', 'int', raw) if method == 'setObject' else ('number', 'float', raw)
            elif re.fullmatch(r'-?\d+[lL]', raw):
                entry = ('object' if method == 'setObject' else 'number', 'long', raw[:-1])
            elif m := re.fullmatch(r'Double\.valueOf\((-?[\d.]+)\)', raw):
                entry = ('number', 'double', m.group(1))
            if entry is None:
                continue
            key = (name, entry[0])
            values.setdefault(key, entry)
    return values


EXTERNAL_CLASSES = [
    'org.unlaxer.tinyexpression.Fee',
    'org.unlaxer.tinyexpression.parser.TestSideEffector',
    'org.unlaxer.tinyexpression.parser.AdmissionFee',
    'CheckDigits',
    'sample.v1.CheckAlphabets',
]


def profiles(formula, test_values):
    names = sorted(set(VAR_RE.findall(formula)))
    rows = [('empty', [], False)]
    if not names and 'external' not in formula and 'nowHour' not in formula:
        if re.search(r'inDayTimeRange|inTimeRange', formula):
            names = []
        else:
            return rows
    time_vars = ['nowHour', 'nowDayOfWeek'] if re.search(r'inDayTimeRange|inTimeRange', formula) else []
    names = sorted(set(names) | set(time_vars))
    rows.append(('num', [['number', 'float', n, num_for(n)] for n in names], True))
    rows.append(('str', [['string', 'string', n, str_for(n, formula)] for n in names]
                 + [['number', 'float', n, num_for(n)] for n in time_vars], True))
    rows.append(('bool', [['boolean', 'boolean', n, bool_for(n)] for n in names]
                 + [['number', 'float', n, num_for(n)] for n in time_vars], True))
    mixed = []
    for n in names:
        mixed.append(['number', 'double' if h(n, 'dbl') % 3 == 0 else 'float', n, num_for(n)])
        mixed.append(['string', 'string', n, str_for(n, formula)])
        mixed.append(['boolean', 'boolean', n, bool_for(n)])
    rows.append(('mixed', mixed, True))
    objs = []
    for n in names:
        k = h(n, 'obj') % 4
        if k == 0:
            objs.append(['object', 'int', n, str(int(float(num_for(n))))])
        elif k == 1:
            objs.append(['object', 'string', n, str_for(n, formula)])
        elif k == 2:
            objs.append(['object', 'boolean', n, bool_for(n)])
        else:
            objs.append(['object', 'long', n, str(int(float(num_for(n))) * 1000)])
    rows.append(('obj', objs + [['number', 'float', n, num_for(n)] for n in time_vars], True))
    tests = []
    for n in names:
        found = [v for (name, _m), v in test_values.items() if name == n]
        if not found:
            found = [('number', 'float', num_for(n))]
        for m, t, v in found:
            tests.append([m, t, n, v])
    rows.append(('tests', tests, True))
    return rows


def main():
    cases = {}

    def add(origin, formula, result_types, number_type=None):
        if formula is None or not plausible(formula):
            return
        for rt in result_types:
            key = (formula, rt, number_type or 'float')
            if key not in cases:
                cases[key] = origin

    all_types = ['float', 'boolean', 'string', 'object']
    # Java tests
    for path in sorted((ROOT / 'src' / 'test').rglob('*.java')):
        rel = path.relative_to(ROOT).as_posix()
        for lit in java_literals(path.read_text(encoding='utf-8', errors='replace')):
            add(rel, lit, all_types)
    # Rust tests
    rust_tests = ROOT / 'rust' / 'tinyexpression-rs' / 'tests'
    for path in sorted(rust_tests.glob('*.rs')):
        if path.name in ('java_differential.rs', 'runtime_api.rs'):
            continue  # harness code, not formulas (and would make the golden depend on itself)
        rel = path.relative_to(ROOT).as_posix()
        for lit in rust_literals(path.read_text(encoding='utf-8')):
            add(rel, lit, all_types)
    fixtures = rust_tests / 'fixtures'
    for path in sorted(fixtures.glob('*.tsv')):
        rel = path.relative_to(ROOT).as_posix()
        for line in path.read_text(encoding='utf-8').split('\n'):
            if not line or line.startswith('#'):
                continue
            fields = line.split('\t')
            if path.name == 'scalar-control.tsv':
                add(rel, fields[2], [norm_type(fields[1]) or 'float'] + all_types)
            else:
                add(rel, fields[1], all_types)
                if path.name == 'numeric-f32.tsv':
                    for nt in ['double', 'int', 'long', 'short', 'byte']:
                        add(rel, fields[1], ['float'], nt)
    tiny = sorted(fixtures.glob('*.tiny')) + sorted((ROOT / 'benchmarks' / 'fixtures').glob('*.tiny'))
    for path in tiny:
        add(path.relative_to(ROOT).as_posix(), path.read_text(encoding='utf-8'), all_types)
    for path in sorted((ROOT / 'src' / 'test' / 'resources' / 'parity').glob('*.txt')):
        rel = path.relative_to(ROOT).as_posix()
        for line in path.read_text(encoding='utf-8').split('\n'):
            if not line.strip() or line.startswith('#'):
                continue
            formula = unescape_java(line) if 'escaped' in path.name else line
            add(rel, formula, all_types)
            if 'escaped' not in path.name and '$' not in formula:
                for nt in ['double', 'int', 'long', 'short', 'byte']:
                    add(rel, formula, ['float'], nt)
    extra = ROOT / 'rust' / 'tinyexpression-rs' / 'tests' / 'java-diff' / 'extra-formulas.tsv'
    for line in extra.read_text(encoding='utf-8').split('\n'):
        if not line or line.startswith('#'):
            continue
        kind, formula = line.split('\t', 1)
        formula = formula.replace('\\n', '\n')
        add(extra.relative_to(ROOT).as_posix(), formula, all_types if kind == '*' else [kind])
    resources = ROOT / 'src' / 'test' / 'resources'
    for path in [resources / 'formulaInfo.fi'] + sorted(resources.glob('formulaInfo-test/*/formulaInfo.txt')):
        rel = path.relative_to(ROOT).as_posix()
        for block in formula_info_blocks(path.read_text(encoding='utf-8')):
            rt = norm_type(block['resultType']) or 'float'
            nt = norm_type(block['numberType'])
            add(rel + '#' + block['name'], block['formula'], [rt], nt)

    test_values = test_context_values()
    out = sys.stdout
    index = 0
    for (formula, rt, nt), origin in cases.items():
        for profile, vars_, externals in profiles(formula, test_values):
            row = {
                'id': f'c{index:05d}', 'origin': origin, 'formula': formula,
                'resultType': rt, 'numberType': nt, 'profile': profile,
                'vars': vars_, 'externals': externals,
            }
            out.write(json.dumps(row, ensure_ascii=False) + '\n')
            index += 1
    print(f'{len(cases)} formula/type cases, {index} rows', file=sys.stderr)


if __name__ == '__main__':
    main()
