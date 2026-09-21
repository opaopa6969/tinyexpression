# packrat memo の position ブロック化実験（2026-09-21、Java、不採用）

## 結論

unlaxer-parser#252。Java parser 単体は 63 倍入力で 70.5 倍（[分離計測](2026-09-21-input-size-scaling.md)）で、complex-x64 の JFR では
parser CPU の上位が `HashMap.resize` 20% / `HashMap.getNode` 8%。`PackratMemoTable` の parser ごとの 1 表を consumed position 256 単位の
ブロックに分割した（Rust #245 の Java 版）。

**不採用。** base fixture は complex -2.58% / comparison-heavy +2.11%（ノイズ内）、x64 は parser 単体 +18.2% / facade +3.1%、x4 / x16 の facade は -11〜-15% と
方向が揃わず、1 run の JMH のばらつき範囲。baseline の parser 単体倍率は 62.4 倍（入力 63 倍）で、Java parser はすでにほぼ線形だった。実装は
unlaxer-parser branch `perf/memo-position-buckets-252`（`522e813`）に保存。

## 測定条件

- unlaxer-parser baseline: `3c64061`（master）、candidate: `522e813`（branch `perf/memo-position-buckets-252`、PR なし）。いずれも isolated Maven repo
- tinyexpression: `514858f`。base fixture は JMH 既定で 3 run、x4 / x16 / x64 は `publicFacade` と `parseOnlySafe` を 1 fork × 5 iteration で各 1 run

## Timing（Java、ms/op）

base fixture（3-run 中央値）:

| Runtime | Fixture | Baseline runs | Baseline median | Candidate runs | Candidate median | 変化 |
|---|---|---:|---:|---:|---:|---:|
| Java | complex | 46.283 / 42.047 / 44.200 | **44.200** | 44.649 / 43.062 / 42.614 | **43.062** | **-2.58%** |
| Java | comparison-heavy | 26.240 / 27.066 / 25.597 | **26.240** | 25.231 / 27.057 / 26.794 | **26.794** | **+2.11%** |

x1 / x4 / x16 / x64（1 run）:

parseOnlySafe:

| Fixture | サイズ倍率 | baseline ms（倍率） | candidate ms（倍率） | 変化 |
|---|---:|---:|---:|---:|
| complex | 1.0x | 36.8（1.0x） | 34.9（1.0x） | -5.0% |
| complex-x4 | 3.9x | 143.4（3.9x） | 158.4（4.5x） | +10.4% |
| complex-x16 | 15.6x | 618.2（16.8x） | 599.8（17.2x） | -3.0% |
| complex-x64 | 63.0x | 2293.5（62.4x） | 2711.4（77.6x） | +18.2% |

publicFacade:

| Fixture | サイズ倍率 | baseline ms（倍率） | candidate ms（倍率） | 変化 |
|---|---:|---:|---:|---:|
| complex | 1.0x | 42.1（1.0x） | 42.1（1.0x） | -0.1% |
| complex-x4 | 3.9x | 192.4（4.6x） | 164.3（3.9x） | -14.6% |
| complex-x16 | 15.6x | 760.2（18.1x） | 675.0（16.0x） | -11.2% |
| complex-x64 | 63.0x | 3774.2（89.6x） | 3890.9（92.5x） | +3.1% |

## 正確性

- unlaxer-parser: Java 672 + 987 tests
- TinyExpression: p4-smoke 85 tests（candidate repo）

## 生データ

`raw/2026-09-21-memo-position-blocks/`: JMH `java-{baseline,candidate}-run{1,2,3}.json`、`java-scaled-{baseline,candidate}.json`
