// Sample formulas with the CalculationContext each one needs (issue #201).
const v = (name, type, value) => ({ name, type, value: String(value) });

export const EXAMPLES = [
  {
    id: 'basic',
    title: '四則演算',
    formula: '1 + 2 * 3',
    context: { resultType: 'float', variables: [] },
  },
  {
    id: 'member',
    title: '条件分岐と変数',
    formula: 'if($member & $age >= 18){100}else{0}',
    context: { resultType: 'float', variables: [v('member', 'boolean', true), v('age', 'float', 20)] },
  },
  {
    id: 'grade',
    title: 'match（文字列の結果）',
    formula: "match{\n  $score >= 80 -> 'A',\n  $score >= 60 -> 'B',\n  default -> 'C'\n}",
    context: { resultType: 'string', variables: [v('score', 'float', 72)] },
  },
  {
    id: 'business-hours',
    title: '営業時間（nowHour / nowDayOfWeek）',
    formula: 'if(inDayTimeRange(MONDAY, 9, FRIDAY, 18)){1}else{0}',
    context: { resultType: 'float', variables: [], nowHour: '10', nowDayOfWeek: 'WEDNESDAY' },
  },
  {
    id: 'fraud-alert',
    title: 'fraud-alert 級の判定式',
    formula: 'if((isPresent($countryCode)&$countryCode!="JP")\n  &((isPresent($osGroup)&toLowerCase($osGroup).in("ios"))\n    &(isPresent($browserGroup)&toLowerCase($browserGroup).contains("safari")))\n  &(((isPresent($timezone)&$timezone==\'+9\')&(isPresent($priorityLanguage)&not($priorityLanguage.contains(\'ja\'))))\n    |((isPresent($timezone)&$timezone!=\'+9\')&(isPresent($priorityLanguage)&$priorityLanguage.contains(\'ja\'))))\n){1}else{0}',
    context: {
      resultType: 'float',
      variables: [
        v('countryCode', 'string', 'US'),
        v('osGroup', 'string', 'iOS'),
        v('browserGroup', 'string', 'Mobile Safari'),
        v('timezone', 'string', '+9'),
        v('priorityLanguage', 'string', 'en-US'),
      ],
    },
  },
  {
    id: 'fraud-score',
    title: 'fraud-alert のスコア式（入れ子 if）',
    formula: 'if(not(isPresent($calculated_FirstAccessUserHash))){1}else{\n  if($ForcedRelativeSuspiciousValue1){1}else{\n    if($ForcedRelativeSuspiciousValue5){5}else{\n      if($default_RelativeSuspiciousValue==5){5}else{\n        if(($POST_PROCESS_OriginalSpec_CountryIsNotJapan>0.0)|($POST_PROCESS_OriginalSpec_BlackListOnOtherSites>0.0)){5}\n        else{$default_RelativeSuspiciousValue}}}}}',
    context: {
      resultType: 'float',
      variables: [
        v('calculated_FirstAccessUserHash', 'string', 'abc'),
        v('ForcedRelativeSuspiciousValue1', 'boolean', false),
        v('ForcedRelativeSuspiciousValue5', 'boolean', false),
        v('default_RelativeSuspiciousValue', 'float', 3),
        v('POST_PROCESS_OriginalSpec_CountryIsNotJapan', 'float', 0),
        v('POST_PROCESS_OriginalSpec_BlackListOnOtherSites', 'float', 1),
      ],
    },
  },
  {
    id: 'external',
    title: 'external（スタブ）',
    formula: 'external returning as number org.unlaxer.tinyexpression.Fee#calculate($age, 1000, 0.1)',
    context: {
      resultType: 'float',
      variables: [v('age', 'float', 30)],
      externals: [{ class: 'org.unlaxer.tinyexpression.Fee', method: 'calculate', arity: '3', registered: true, returnType: 'float', value: '1100' }],
    },
  },
  {
    // Issue #216: the Java code block is coloured but not run; the stub answers the call.
    id: 'java-code-block',
    title: 'Java コードブロック（仮の値で代用）',
    formula: [
      '```java:CheckDigits',
      'import org.unlaxer.tinyexpression.CalculationContext;',
      '',
      'public class CheckDigits{',
      '\tpublic boolean check(CalculationContext calculationContext,String target){',
      '\t\treturn target.matches("\\\\d+");',
      '\t}',
      '}',
      '```',
      'import CheckDigits#check as checkDigits;',
      "var $input as string set if not exists 'not number' description='入力値';",
      'if(external returning as boolean checkDigits($input)){',
      '  1',
      '}else{',
      '  0',
      '}',
    ].join('\n'),
    context: {
      resultType: 'float',
      variables: [],
      externals: [{ class: 'CheckDigits', method: 'check', arity: '1', registered: true, returnType: 'boolean', value: 'true' }],
    },
  },
  {
    id: 'broken',
    title: '構文エラー（TE コード）',
    formula: 'var $x as number set 1 description=\'x\'\n$x + 1',
    context: { resultType: 'float', variables: [] },
  },
];

export const FORMULA_INFO_SAMPLE = `tags:NORMAL
description:基本点にボーナスを足します
calculatorName:totalScore
resultType:float
executionBackend:P4_AST_EVALUATOR
formula:
var $base as float set if not exists 40;
$base + $bonus
---END_OF_PART---
calculatorName:memberOnly
resultType:boolean
formula:
$member & $age >= 18
---END_OF_PART---
`;
