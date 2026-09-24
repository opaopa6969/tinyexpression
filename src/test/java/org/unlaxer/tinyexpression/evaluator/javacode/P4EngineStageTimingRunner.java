package org.unlaxer.tinyexpression.evaluator.javacode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.unlaxer.compiler.ClassAndByteCode;
import org.unlaxer.compiler.ClassName;
import org.unlaxer.compiler.CompileContext;
import org.unlaxer.compiler.JavaFileManagerContext;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.TokenBaseCalculator;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * parse / Java コード生成 / javac / 実行 の 4 段を別々に測る（issue #183 の段別時間表）。
 * テストではなく手で走らせる計測器（surefire の対象外の名前）。エンジンは
 * {@code -Dtinyexpression.p4.engine=ubnfc|legacy} で選ぶ。移植元は ubnfc
 * {@code examples/p4-java-facade} の StageTimingRunner（classpath shadow の代わりにエンジン切替を使う）。
 *
 * <pre>
 * mvn -o -q test-compile -Dtinyexpression.skipRailroad=true
 * mvn -o -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -Xss16m -Dtinyexpression.p4.engine=legacy -cp target/classes:target/test-classes:$(cat target/cp.txt) \
 *   org.unlaxer.tinyexpression.evaluator.javacode.P4EngineStageTimingRunner target/timing/legacy.tsv
 * </pre>
 */
public final class P4EngineStageTimingRunner {

    private static final int WARMUP = Integer.getInteger("timing.warmup", 3);
    private static final int RUNS = Integer.getInteger("timing.runs", 9);
    private static final int EXECUTE_ITERATIONS = Integer.getInteger("timing.execute", 20_000);

    private record Input(String name, String formula, ExpressionType resultType) {}

    private record Stage(String name, double millis, String note) {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/timing/stages.tsv");
        List<Input> inputs = inputs();
        var rows = new ArrayList<String>();
        rows.add("input\tchars\tparse_ms\tgenerate_ms\tjavac_ms\texecute_us\tnote");
        for (Input input : inputs) {
            Map<String, Stage> stages = measure(input);
            rows.add(input.name() + "\t" + input.formula().length() + "\t"
                + format(stages.get("parse")) + "\t"
                + format(stages.get("generate")) + "\t"
                + format(stages.get("javac")) + "\t"
                + format(stages.get("execute")) + "\t"
                + stages.values().stream().map(Stage::note).filter(note -> !note.isEmpty())
                    .findFirst().orElse(""));
            System.out.println(rows.get(rows.size() - 1));
        }
        rows.add(formulaInfoRow());
        System.out.println(rows.get(rows.size() - 1));
        Files.createDirectories(out.getParent());
        Files.writeString(out, String.join("\n", rows) + "\n");
        System.out.println("engine: " + P4PreferredAstMapper.engine().id());
    }

    private static String format(Stage stage) {
        return stage == null ? "-" : String.format("%.3f", stage.millis());
    }

    private static List<Input> inputs() throws Exception {
        var inputs = new ArrayList<Input>();
        Path fixtures = System.getProperty("timing.fixtures") != null
            ? Path.of(System.getProperty("timing.fixtures"))
            : Path.of(P4EngineStageTimingRunner.class.getResource("/p4/ubnfc-parity/fixtures").toURI());
        if (Files.isDirectory(fixtures)) {
            try (var stream = Files.list(fixtures)) {
                for (Path file : stream.sorted().toList()) {
                    if (!file.getFileName().toString().endsWith(".tiny")) continue;
                    inputs.add(new Input("fixture:" + file.getFileName(),
                        Files.readString(file), ExpressionTypes._float));
                }
            }
        }
        String[] fraud = fraudFormulas();
        for (int index = 0; index < fraud.length; index++) {
            inputs.add(new Input("fraud#" + (index + 1), fraud[index], ExpressionTypes._float));
        }
        return inputs;
    }

    private static Map<String, Stage> measure(Input input) {
        var stages = new LinkedHashMap<String, Stage>();
        double parse = median(() -> {
            P4PreferredAstMapper.parseDetailed(input.formula(), input.resultType());
        });
        if (Double.isNaN(parse)) {
            stages.put("parse", new Stage("parse", Double.NaN, "parse 不可"));
            return stages;
        }
        stages.put("parse", new Stage("parse", parse, ""));

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        SpecifiedExpressionTypes types =
            new SpecifiedExpressionTypes(input.resultType(), ExpressionTypes._float);
        String className = "StageTiming_" + Integer.toHexString(input.name().hashCode())
            + "_" + Integer.toHexString(input.formula().hashCode());
        Optional<DslGeneratedAstJavaEmitter.EmittedJava> emitted;
        try {
            emitted = DslGeneratedAstJavaEmitter.tryEmit(
                className, new Source(input.formula()), types, loader);
        } catch (RuntimeException failure) {
            emitted = Optional.empty();
        }
        if (emitted.isEmpty()) {
            stages.put("generate", new Stage("generate", Double.NaN, "Java 生成が非対応"));
            return stages;
        }
        String javaCode = emitted.orElseThrow().javaCode();
        double emit = median(() -> DslGeneratedAstJavaEmitter.tryEmit(
            className, new Source(input.formula()), types, loader));
        // tryEmit は内部で parse も行うので、生成そのものは差で出す。
        stages.put("generate", new Stage("generate", Math.max(0.0, emit - parse), ""));

        double javac = median(() -> {
            try (CompileContext context = new CompileContext(loader, new JavaFileManagerContext())) {
                context.compile(new ClassName(className), javaCode).get();
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(failure);
            }
        });
        stages.put("javac", new Stage("javac", javac, ""));

        try (CompileContext context = new CompileContext(loader, new JavaFileManagerContext())) {
            ClassAndByteCode compiled =
                context.compile(new ClassName(className), javaCode).get();
            TokenBaseCalculator calculator =
                (TokenBaseCalculator) compiled.clazz.getDeclaredConstructor().newInstance();
            CalculationContext calculation = CalculationContext.newConcurrentContext();
            for (int i = 0; i < 1_000; i++) evaluateQuietly(calculator, calculation);
            long start = System.nanoTime();
            for (int i = 0; i < EXECUTE_ITERATIONS; i++) evaluateQuietly(calculator, calculation);
            double micros = (System.nanoTime() - start) / 1_000.0 / EXECUTE_ITERATIONS;
            stages.put("execute", new Stage("execute", micros, ""));
        } catch (Exception failure) {
            stages.put("execute", new Stage("execute", Double.NaN, "実行不可: " + failure));
        }

        return stages;
    }

    private static void evaluateQuietly(TokenBaseCalculator calculator, CalculationContext context) {
        try {
            calculator.evaluate(context, null);
        } catch (RuntimeException tolerated) {
            // 変数未束縛などは時間の測定対象外。失敗も同じ経路を通るので計測は成立する。
        }
    }

    private static double median(Runnable action) {
        try {
            for (int i = 0; i < WARMUP; i++) action.run();
        } catch (RuntimeException | StackOverflowError failure) {
            return Double.NaN;
        }
        double[] samples = new double[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            try {
                action.run();
            } catch (RuntimeException | StackOverflowError failure) {
                return Double.NaN;
            }
            samples[i] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(samples);
        return samples[RUNS / 2];
    }

    /** fraud-alert 相当の FormulaInfo 文書を組んで、読み込みから実行まで通す。 */
    private static String formulaInfoRow() {
        String document = formulaInfoDocument();
        double millis = median(() -> {
            var fields = new org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields(
                "siteId", formulaInfo -> formulaInfo.calculatorName);
            fields.setExecutionBackend(
                org.unlaxer.tinyexpression.runtime.ExecutionBackend.DSL_JAVA_CODE);
            var parsed = org.unlaxer.tinyexpression.loader.model.FormulaInfoList.parse(
                document, fields, Thread.currentThread().getContextClassLoader());
            parsed.throwable.ifPresent(failure -> {
                throw new IllegalStateException(failure);
            });
        });
        return "formulaInfo:fraud-alert\t" + document.length() + "\t-\t-\t-\t-\t"
            + (Double.isNaN(millis) ? "読み込み不可" : String.format("読み込み〜実行可能まで %.1f ms", millis));
    }

    private static String formulaInfoDocument() {
        var text = new StringBuilder();
        String[] fraud = fraudFormulas();
        for (int index = 0; index < fraud.length; index++) {
            text.append("tags:FRAUD\n")
                .append("description:fraud-alert 相当 #").append(index + 1).append('\n')
                .append("periodStartInclusive:2018-05-01_00:00:00\n")
                .append("periodEndExclusive:2037-05-01_00:00:00\n")
                .append("calculatorName:FraudAlert").append(index + 1).append('\n')
                .append("backend:dsl-javacode\n")
                .append("resultType:float\n")
                .append("formula:\n")
                .append(fraud[index]).append('\n')
                .append("---END_OF_PART---\n");
        }
        return text.toString();
    }

    /** issue #19 の実運用 fraud-alert 式（tinyexpression の P4PackratFraudFormulaTest と同じ 5 本）。 */
    private static String[] fraudFormulas() {
        return new String[] {
            "if((isPresent($countryCode)&$countryCode!=\"JP\")&((isPresent($osGroup)&toLowerCase($osGroup).in(\"ios\"))&(isPresent($browserGroup)&toLowerCase($browserGroup).contains(\"safari\")))&(((isPresent($timezone)&$timezone=='+9')&(isPresent($priorityLanguage)&not($priorityLanguage.contains('ja'))))|((isPresent($timezone)&$timezone!='+9')&(isPresent($priorityLanguage)&$priorityLanguage.contains('ja'))))){1}else{0}",
            "if((isPresent($calculated_BlackIPAddressInOtherSites)&$calculated_BlackIPAddressInOtherSites>0.0)|(isPresent($calculated_BlackCaulisCookieInOtherSites)&$calculated_BlackCaulisCookieInOtherSites>0.0)){1}else{0}",
            "if(isPresent($calculated_TorNode)&$calculated_TorNode>0.0){1}else{0}",
            "if((isPresent($userCountGroupedByCookieOnThisSite)&$userCountGroupedByCookieOnThisSite>=2)&((isPresent($os)&(not(toLowerCase($os).contains(\"linux\"))|not(toLowerCase($os).contains(\"Fire OS\"))))|(isPresent($number_accountCreationCountByIpAddress)&isPresent($userCountGroupedByCookieOnThisSite)&not($number_accountCreationCountByIpAddress - $userCountGroupedByCookieOnThisSite>=1))|(isPresent($userCountGroupedByCookieOnAllSite)&isPresent($userCountGroupedByCookieOnThisSite)&isPresent($userCountGroupedByCookieOnThisSiteOn12H)&(not($userCountGroupedByCookieOnAllSite - $userCountGroupedByCookieOnThisSite>=1)&not($userCountGroupedByCookieOnThisSite - $userCountGroupedByCookieOnThisSiteOn12H==0))))){1}else{0}",
            "if(not(isPresent($calculated_FirstAccessUserHash))){1}else{if($ForcedRelativeSuspiciousValue1){1}else{if($ForcedRelativeSuspiciousValue5){5}else{if($default_RelativeSuspiciousValue==5){5}else{if(($POST_PROCESS_OriginalSpec_CountryIsNotJapan>0.0)|($POST_PROCESS_OriginalSpec_BlackListOnOtherSites>0.0)|($POST_PROCESS_OriginalSpec_SuspiciousProvider>0.0)|($POST_PROCESS_OriginalSpec_OneUserAccessToMultiAccount>0.0)){5}else{$default_RelativeSuspiciousValue}}}}}"
        };
    }

    private P4EngineStageTimingRunner() {}
}
