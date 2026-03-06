# Railroad Diagrams

Auto-generated from: `tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf` (latest)

**Note:** Latest grammar version includes CodeBlock support and all P4 language features. Previous snapshot was `docs/ubnf/tinyexpression-p4-complete.ubnf`.

## Table of Contents

### TinyExpressionP4

- [Formula](#formula)
- [CodeBlock](#codeblock)
- [ImportDeclaration](#importdeclaration)
- [ImportDeclarationWithMethod](#importdeclarationwithmethod)
- [ImportDeclarationBare](#importdeclarationbare)
- [ClassName](#classname)
- [VariableDeclaration](#variabledeclaration)
- [NumberVariableDeclaration](#numbervariabledeclaration)
- [StringVariableDeclaration](#stringvariabledeclaration)
- [BooleanVariableDeclaration](#booleanvariabledeclaration)
- [ObjectVariableDeclaration](#objectvariabledeclaration)
- [TypeHint](#typehint)
- [NumberTypeHint](#numbertypehint)
- [StringTypeHint](#stringtypehint)
- [BooleanTypeHint](#booleantypehint)
- [ObjectTypeHint](#objecttypehint)
- [NumberSetter](#numbersetter)
- [StringSetter](#stringsetter)
- [BooleanSetter](#booleansetter)
- [ObjectSetter](#objectsetter)
- [Description](#description)
- [Annotation](#annotation)
- [AnnotationParameters](#annotationparameters)
- [AnnotationParameter](#annotationparameter)
- [MethodDeclaration](#methoddeclaration)
- [NumberMethodDeclaration](#numbermethoddeclaration)
- [StringMethodDeclaration](#stringmethoddeclaration)
- [BooleanMethodDeclaration](#booleanmethoddeclaration)
- [ObjectMethodDeclaration](#objectmethoddeclaration)
- [MethodParameters](#methodparameters)
- [MethodParameter](#methodparameter)
- [NumberReturnType](#numberreturntype)
- [StringReturnType](#stringreturntype)
- [BooleanReturnType](#booleanreturntype)
- [ObjectReturnType](#objectreturntype)
- [ReturnType](#returntype)
- [ExternalBooleanInvocation](#externalbooleaninvocation)
- [ExternalNumberInvocation](#externalnumberinvocation)
- [ExternalStringInvocation](#externalstringinvocation)
- [ExternalObjectInvocation](#externalobjectinvocation)
- [SideEffectHeader](#sideeffectheader)
- [SideEffectNumberExpression](#sideeffectnumberexpression)
- [SideEffectStringExpression](#sideeffectstringexpression)
- [SideEffectBooleanExpression](#sideeffectbooleanexpression)
- [QualifiedMethod](#qualifiedmethod)
- [MethodInvocationHeader](#methodinvocationheader)
- [MethodInvocation](#methodinvocation)
- [Arguments](#arguments)
- [NumberExpression](#numberexpression)
- [NumberTerm](#numberterm)
- [AddOp](#addop)
- [MulOp](#mulop)
- [NumberFactor](#numberfactor)
- [TernaryExpression](#ternaryexpression)
- [MathFunction](#mathfunction)
- [SinFunction](#sinfunction)
- [CosFunction](#cosfunction)
- [TanFunction](#tanfunction)
- [SqrtFunction](#sqrtfunction)
- [MinFunction](#minfunction)
- [MaxFunction](#maxfunction)
- [RandomFunction](#randomfunction)
- [ToNumFunction](#tonumfunction)
- [StringExpression](#stringexpression)
- [StringTerm](#stringterm)
- [StringMethodCall](#stringmethodcall)
- [ToUpperCaseMethod](#touppercasemethod)
- [ToLowerCaseMethod](#tolowercasemethod)
- [TrimMethod](#trimmethod)
- [Slice](#slice)
- [BooleanExpression](#booleanexpression)
- [BooleanAndExpression](#booleanandexpression)
- [BooleanXorExpression](#booleanxorexpression)
- [BooleanFactor](#booleanfactor)
- [NotExpression](#notexpression)
- [StringComparisonExpression](#stringcomparisonexpression)
- [EqualityOp](#equalityop)
- [ComparisonExpression](#comparisonexpression)
- [CompareOp](#compareop)
- [IsPresentFunction](#ispresentfunction)
- [InTimeRangeFunction](#intimerangefunction)
- [InDayTimeRangeFunction](#indaytimerangefunction)
- [DayOfWeek](#dayofweek)
- [StringPredicateMethod](#stringpredicatemethod)
- [IndexOfMethod](#indexofmethod)
- [StartsWithMethod](#startswithmethod)
- [EndsWithMethod](#endswithmethod)
- [ContainsMethod](#containsmethod)
- [InMethod](#inmethod)
- [CommaSeparatedStrings](#commaseparatedstrings)
- [ObjectExpression](#objectexpression)
- [IfExpression](#ifexpression)
- [NumberMatchExpression](#numbermatchexpression)
- [NumberCase](#numbercase)
- [NumberDefaultCase](#numberdefaultcase)
- [NumberCaseValue](#numbercasevalue)
- [StringMatchExpression](#stringmatchexpression)
- [StringCase](#stringcase)
- [StringDefaultCase](#stringdefaultcase)
- [StringCaseValue](#stringcasevalue)
- [BooleanMatchExpression](#booleanmatchexpression)
- [BooleanCase](#booleancase)
- [BooleanDefaultCase](#booleandefaultcase)
- [BooleanCaseValue](#booleancasevalue)
- [VariableRef](#variableref)
- [Expression](#expression)

## TinyExpressionP4

### Formula

![Formula](TinyExpressionP4_Formula.svg)

**References:** [Annotation](#annotation), [CodeBlock](#codeblock), [Expression](#expression), [ImportDeclaration](#importdeclaration), [MethodDeclaration](#methoddeclaration), [VariableDeclaration](#variabledeclaration)

### CodeBlock

![CodeBlock](TinyExpressionP4_CodeBlock.svg)

**References:** [CODE_BLOCK](#code_block)

### ImportDeclaration

![ImportDeclaration](TinyExpressionP4_ImportDeclaration.svg)

**References:** [ImportDeclarationBare](#importdeclarationbare), [ImportDeclarationWithMethod](#importdeclarationwithmethod)

### ImportDeclarationWithMethod

![ImportDeclarationWithMethod](TinyExpressionP4_ImportDeclarationWithMethod.svg)

**References:** [ClassName](#classname), [IDENTIFIER](#identifier)

### ImportDeclarationBare

![ImportDeclarationBare](TinyExpressionP4_ImportDeclarationBare.svg)

**References:** [ClassName](#classname), [IDENTIFIER](#identifier)

### ClassName

![ClassName](TinyExpressionP4_ClassName.svg)

**References:** [IDENTIFIER](#identifier)

### VariableDeclaration

![VariableDeclaration](TinyExpressionP4_VariableDeclaration.svg)

**References:** [BooleanVariableDeclaration](#booleanvariabledeclaration), [NumberVariableDeclaration](#numbervariabledeclaration), [ObjectVariableDeclaration](#objectvariabledeclaration), [StringVariableDeclaration](#stringvariabledeclaration)

### NumberVariableDeclaration

![NumberVariableDeclaration](TinyExpressionP4_NumberVariableDeclaration.svg)

**References:** [Description](#description), [IDENTIFIER](#identifier), [NumberSetter](#numbersetter), [NumberTypeHint](#numbertypehint)

### StringVariableDeclaration

![StringVariableDeclaration](TinyExpressionP4_StringVariableDeclaration.svg)

**References:** [Description](#description), [IDENTIFIER](#identifier), [StringSetter](#stringsetter), [StringTypeHint](#stringtypehint)

### BooleanVariableDeclaration

![BooleanVariableDeclaration](TinyExpressionP4_BooleanVariableDeclaration.svg)

**References:** [BooleanSetter](#booleansetter), [BooleanTypeHint](#booleantypehint), [Description](#description), [IDENTIFIER](#identifier)

### ObjectVariableDeclaration

![ObjectVariableDeclaration](TinyExpressionP4_ObjectVariableDeclaration.svg)

**References:** [Description](#description), [IDENTIFIER](#identifier), [ObjectSetter](#objectsetter), [ObjectTypeHint](#objecttypehint)

### TypeHint

![TypeHint](TinyExpressionP4_TypeHint.svg)

### NumberTypeHint

![NumberTypeHint](TinyExpressionP4_NumberTypeHint.svg)

### StringTypeHint

![StringTypeHint](TinyExpressionP4_StringTypeHint.svg)

### BooleanTypeHint

![BooleanTypeHint](TinyExpressionP4_BooleanTypeHint.svg)

### ObjectTypeHint

![ObjectTypeHint](TinyExpressionP4_ObjectTypeHint.svg)

### NumberSetter

![NumberSetter](TinyExpressionP4_NumberSetter.svg)

**References:** [NumberExpression](#numberexpression)

### StringSetter

![StringSetter](TinyExpressionP4_StringSetter.svg)

**References:** [StringExpression](#stringexpression)

### BooleanSetter

![BooleanSetter](TinyExpressionP4_BooleanSetter.svg)

**References:** [BooleanExpression](#booleanexpression)

### ObjectSetter

![ObjectSetter](TinyExpressionP4_ObjectSetter.svg)

**References:** [ObjectExpression](#objectexpression)

### Description

![Description](TinyExpressionP4_Description.svg)

**References:** [STRING](#string)

### Annotation

![Annotation](TinyExpressionP4_Annotation.svg)

**References:** [AnnotationParameters](#annotationparameters), [IDENTIFIER](#identifier)

### AnnotationParameters

![AnnotationParameters](TinyExpressionP4_AnnotationParameters.svg)

**References:** [AnnotationParameter](#annotationparameter)

### AnnotationParameter

![AnnotationParameter](TinyExpressionP4_AnnotationParameter.svg)

**References:** [Expression](#expression), [IDENTIFIER](#identifier)

### MethodDeclaration

![MethodDeclaration](TinyExpressionP4_MethodDeclaration.svg)

**References:** [BooleanMethodDeclaration](#booleanmethoddeclaration), [NumberMethodDeclaration](#numbermethoddeclaration), [ObjectMethodDeclaration](#objectmethoddeclaration), [StringMethodDeclaration](#stringmethoddeclaration)

### NumberMethodDeclaration

![NumberMethodDeclaration](TinyExpressionP4_NumberMethodDeclaration.svg)

**References:** [IDENTIFIER](#identifier), [MethodParameters](#methodparameters), [NumberExpression](#numberexpression), [NumberReturnType](#numberreturntype)

### StringMethodDeclaration

![StringMethodDeclaration](TinyExpressionP4_StringMethodDeclaration.svg)

**References:** [IDENTIFIER](#identifier), [MethodParameters](#methodparameters), [StringExpression](#stringexpression), [StringReturnType](#stringreturntype)

### BooleanMethodDeclaration

![BooleanMethodDeclaration](TinyExpressionP4_BooleanMethodDeclaration.svg)

**References:** [BooleanExpression](#booleanexpression), [BooleanReturnType](#booleanreturntype), [IDENTIFIER](#identifier), [MethodParameters](#methodparameters)

### ObjectMethodDeclaration

![ObjectMethodDeclaration](TinyExpressionP4_ObjectMethodDeclaration.svg)

**References:** [IDENTIFIER](#identifier), [MethodParameters](#methodparameters), [ObjectExpression](#objectexpression), [ObjectReturnType](#objectreturntype)

### MethodParameters

![MethodParameters](TinyExpressionP4_MethodParameters.svg)

**References:** [MethodParameter](#methodparameter)

### MethodParameter

![MethodParameter](TinyExpressionP4_MethodParameter.svg)

**References:** [IDENTIFIER](#identifier), [ReturnType](#returntype)

### NumberReturnType

![NumberReturnType](TinyExpressionP4_NumberReturnType.svg)

### StringReturnType

![StringReturnType](TinyExpressionP4_StringReturnType.svg)

### BooleanReturnType

![BooleanReturnType](TinyExpressionP4_BooleanReturnType.svg)

### ObjectReturnType

![ObjectReturnType](TinyExpressionP4_ObjectReturnType.svg)

### ReturnType

![ReturnType](TinyExpressionP4_ReturnType.svg)

**References:** [BooleanReturnType](#booleanreturntype), [NumberReturnType](#numberreturntype), [ObjectReturnType](#objectreturntype), [StringReturnType](#stringreturntype)

### ExternalBooleanInvocation

![ExternalBooleanInvocation](TinyExpressionP4_ExternalBooleanInvocation.svg)

**References:** [Arguments](#arguments), [BooleanReturnType](#booleanreturntype), [IDENTIFIER](#identifier)

### ExternalNumberInvocation

![ExternalNumberInvocation](TinyExpressionP4_ExternalNumberInvocation.svg)

**References:** [Arguments](#arguments), [IDENTIFIER](#identifier), [NumberReturnType](#numberreturntype)

### ExternalStringInvocation

![ExternalStringInvocation](TinyExpressionP4_ExternalStringInvocation.svg)

**References:** [Arguments](#arguments), [IDENTIFIER](#identifier), [StringReturnType](#stringreturntype)

### ExternalObjectInvocation

![ExternalObjectInvocation](TinyExpressionP4_ExternalObjectInvocation.svg)

**References:** [Arguments](#arguments), [IDENTIFIER](#identifier), [ObjectReturnType](#objectreturntype)

### SideEffectHeader

![SideEffectHeader](TinyExpressionP4_SideEffectHeader.svg)

### SideEffectNumberExpression

![SideEffectNumberExpression](TinyExpressionP4_SideEffectNumberExpression.svg)

**References:** [Arguments](#arguments), [NumberReturnType](#numberreturntype), [QualifiedMethod](#qualifiedmethod), [SideEffectHeader](#sideeffectheader)

### SideEffectStringExpression

![SideEffectStringExpression](TinyExpressionP4_SideEffectStringExpression.svg)

**References:** [Arguments](#arguments), [QualifiedMethod](#qualifiedmethod), [SideEffectHeader](#sideeffectheader), [StringReturnType](#stringreturntype)

### SideEffectBooleanExpression

![SideEffectBooleanExpression](TinyExpressionP4_SideEffectBooleanExpression.svg)

**References:** [Arguments](#arguments), [BooleanReturnType](#booleanreturntype), [QualifiedMethod](#qualifiedmethod), [SideEffectHeader](#sideeffectheader)

### QualifiedMethod

![QualifiedMethod](TinyExpressionP4_QualifiedMethod.svg)

**References:** [ClassName](#classname), [IDENTIFIER](#identifier)

### MethodInvocationHeader

![MethodInvocationHeader](TinyExpressionP4_MethodInvocationHeader.svg)

### MethodInvocation

![MethodInvocation](TinyExpressionP4_MethodInvocation.svg)

**References:** [Arguments](#arguments), [IDENTIFIER](#identifier), [MethodInvocationHeader](#methodinvocationheader)

### Arguments

![Arguments](TinyExpressionP4_Arguments.svg)

**References:** [Expression](#expression)

### NumberExpression

![NumberExpression](TinyExpressionP4_NumberExpression.svg)

**References:** [AddOp](#addop), [NumberTerm](#numberterm)

### NumberTerm

![NumberTerm](TinyExpressionP4_NumberTerm.svg)

**References:** [MulOp](#mulop), [NumberFactor](#numberfactor)

### AddOp

![AddOp](TinyExpressionP4_AddOp.svg)

### MulOp

![MulOp](TinyExpressionP4_MulOp.svg)

### NumberFactor

![NumberFactor](TinyExpressionP4_NumberFactor.svg)

**References:** [ExternalNumberInvocation](#externalnumberinvocation), [IfExpression](#ifexpression), [MathFunction](#mathfunction), [MethodInvocation](#methodinvocation), [NUMBER](#number), [NumberExpression](#numberexpression), [NumberMatchExpression](#numbermatchexpression), [SideEffectNumberExpression](#sideeffectnumberexpression), [TernaryExpression](#ternaryexpression), [ToNumFunction](#tonumfunction), [VariableRef](#variableref)

### TernaryExpression

![TernaryExpression](TinyExpressionP4_TernaryExpression.svg)

**References:** [BooleanFactor](#booleanfactor), [NumberExpression](#numberexpression)

### MathFunction

![MathFunction](TinyExpressionP4_MathFunction.svg)

**References:** [CosFunction](#cosfunction), [MaxFunction](#maxfunction), [MinFunction](#minfunction), [RandomFunction](#randomfunction), [SinFunction](#sinfunction), [SqrtFunction](#sqrtfunction), [TanFunction](#tanfunction)

### SinFunction

![SinFunction](TinyExpressionP4_SinFunction.svg)

**References:** [NumberExpression](#numberexpression)

### CosFunction

![CosFunction](TinyExpressionP4_CosFunction.svg)

**References:** [NumberExpression](#numberexpression)

### TanFunction

![TanFunction](TinyExpressionP4_TanFunction.svg)

**References:** [NumberExpression](#numberexpression)

### SqrtFunction

![SqrtFunction](TinyExpressionP4_SqrtFunction.svg)

**References:** [NumberExpression](#numberexpression)

### MinFunction

![MinFunction](TinyExpressionP4_MinFunction.svg)

**References:** [NumberExpression](#numberexpression)

### MaxFunction

![MaxFunction](TinyExpressionP4_MaxFunction.svg)

**References:** [NumberExpression](#numberexpression)

### RandomFunction

![RandomFunction](TinyExpressionP4_RandomFunction.svg)

### ToNumFunction

![ToNumFunction](TinyExpressionP4_ToNumFunction.svg)

**References:** [NumberExpression](#numberexpression), [StringExpression](#stringexpression)

### StringExpression

![StringExpression](TinyExpressionP4_StringExpression.svg)

**References:** [StringTerm](#stringterm)

### StringTerm

![StringTerm](TinyExpressionP4_StringTerm.svg)

**References:** [ExternalStringInvocation](#externalstringinvocation), [MethodInvocation](#methodinvocation), [STRING](#string), [SideEffectStringExpression](#sideeffectstringexpression), [StringMatchExpression](#stringmatchexpression), [StringMethodCall](#stringmethodcall), [VariableRef](#variableref)

### StringMethodCall

![StringMethodCall](TinyExpressionP4_StringMethodCall.svg)

**References:** [ToLowerCaseMethod](#tolowercasemethod), [ToUpperCaseMethod](#touppercasemethod), [TrimMethod](#trimmethod)

### ToUpperCaseMethod

![ToUpperCaseMethod](TinyExpressionP4_ToUpperCaseMethod.svg)

**References:** [StringExpression](#stringexpression)

### ToLowerCaseMethod

![ToLowerCaseMethod](TinyExpressionP4_ToLowerCaseMethod.svg)

**References:** [StringExpression](#stringexpression)

### TrimMethod

![TrimMethod](TinyExpressionP4_TrimMethod.svg)

**References:** [StringExpression](#stringexpression)

### Slice

![Slice](TinyExpressionP4_Slice.svg)

**References:** [NumberExpression](#numberexpression)

### BooleanExpression

![BooleanExpression](TinyExpressionP4_BooleanExpression.svg)

**References:** [BooleanAndExpression](#booleanandexpression)

### BooleanAndExpression

![BooleanAndExpression](TinyExpressionP4_BooleanAndExpression.svg)

**References:** [BooleanXorExpression](#booleanxorexpression)

### BooleanXorExpression

![BooleanXorExpression](TinyExpressionP4_BooleanXorExpression.svg)

**References:** [BooleanFactor](#booleanfactor)

### BooleanFactor

![BooleanFactor](TinyExpressionP4_BooleanFactor.svg)

**References:** [BooleanExpression](#booleanexpression), [BooleanMatchExpression](#booleanmatchexpression), [ComparisonExpression](#comparisonexpression), [ExternalBooleanInvocation](#externalbooleaninvocation), [InDayTimeRangeFunction](#indaytimerangefunction), [InTimeRangeFunction](#intimerangefunction), [IsPresentFunction](#ispresentfunction), [MethodInvocation](#methodinvocation), [NotExpression](#notexpression), [SideEffectBooleanExpression](#sideeffectbooleanexpression), [StringComparisonExpression](#stringcomparisonexpression), [StringPredicateMethod](#stringpredicatemethod), [VariableRef](#variableref)

### NotExpression

![NotExpression](TinyExpressionP4_NotExpression.svg)

**References:** [BooleanExpression](#booleanexpression)

### StringComparisonExpression

![StringComparisonExpression](TinyExpressionP4_StringComparisonExpression.svg)

**References:** [EqualityOp](#equalityop), [StringExpression](#stringexpression)

### EqualityOp

![EqualityOp](TinyExpressionP4_EqualityOp.svg)

### ComparisonExpression

![ComparisonExpression](TinyExpressionP4_ComparisonExpression.svg)

**References:** [CompareOp](#compareop), [NumberExpression](#numberexpression)

### CompareOp

![CompareOp](TinyExpressionP4_CompareOp.svg)

### IsPresentFunction

![IsPresentFunction](TinyExpressionP4_IsPresentFunction.svg)

**References:** [VariableRef](#variableref)

### InTimeRangeFunction

![InTimeRangeFunction](TinyExpressionP4_InTimeRangeFunction.svg)

**References:** [NumberExpression](#numberexpression)

### InDayTimeRangeFunction

![InDayTimeRangeFunction](TinyExpressionP4_InDayTimeRangeFunction.svg)

**References:** [DayOfWeek](#dayofweek), [NumberExpression](#numberexpression)

### DayOfWeek

![DayOfWeek](TinyExpressionP4_DayOfWeek.svg)

### StringPredicateMethod

![StringPredicateMethod](TinyExpressionP4_StringPredicateMethod.svg)

**References:** [ContainsMethod](#containsmethod), [EndsWithMethod](#endswithmethod), [InMethod](#inmethod), [IndexOfMethod](#indexofmethod), [StartsWithMethod](#startswithmethod)

### IndexOfMethod

![IndexOfMethod](TinyExpressionP4_IndexOfMethod.svg)

**References:** [StringExpression](#stringexpression)

### StartsWithMethod

![StartsWithMethod](TinyExpressionP4_StartsWithMethod.svg)

**References:** [CommaSeparatedStrings](#commaseparatedstrings), [StringExpression](#stringexpression)

### EndsWithMethod

![EndsWithMethod](TinyExpressionP4_EndsWithMethod.svg)

**References:** [CommaSeparatedStrings](#commaseparatedstrings), [StringExpression](#stringexpression)

### ContainsMethod

![ContainsMethod](TinyExpressionP4_ContainsMethod.svg)

**References:** [CommaSeparatedStrings](#commaseparatedstrings), [StringExpression](#stringexpression)

### InMethod

![InMethod](TinyExpressionP4_InMethod.svg)

**References:** [CommaSeparatedStrings](#commaseparatedstrings), [StringExpression](#stringexpression)

### CommaSeparatedStrings

![CommaSeparatedStrings](TinyExpressionP4_CommaSeparatedStrings.svg)

**References:** [StringExpression](#stringexpression)

### ObjectExpression

![ObjectExpression](TinyExpressionP4_ObjectExpression.svg)

**References:** [BooleanExpression](#booleanexpression), [ExternalObjectInvocation](#externalobjectinvocation), [MethodInvocation](#methodinvocation), [NumberExpression](#numberexpression), [StringExpression](#stringexpression), [VariableRef](#variableref)

### IfExpression

![IfExpression](TinyExpressionP4_IfExpression.svg)

**References:** [BooleanExpression](#booleanexpression), [Expression](#expression)

### NumberMatchExpression

![NumberMatchExpression](TinyExpressionP4_NumberMatchExpression.svg)

**References:** [NumberCase](#numbercase), [NumberDefaultCase](#numberdefaultcase)

### NumberCase

![NumberCase](TinyExpressionP4_NumberCase.svg)

**References:** [BooleanExpression](#booleanexpression), [NumberCaseValue](#numbercasevalue)

### NumberDefaultCase

![NumberDefaultCase](TinyExpressionP4_NumberDefaultCase.svg)

**References:** [NumberCaseValue](#numbercasevalue)

### NumberCaseValue

![NumberCaseValue](TinyExpressionP4_NumberCaseValue.svg)

**References:** [NumberExpression](#numberexpression)

### StringMatchExpression

![StringMatchExpression](TinyExpressionP4_StringMatchExpression.svg)

**References:** [StringCase](#stringcase), [StringDefaultCase](#stringdefaultcase)

### StringCase

![StringCase](TinyExpressionP4_StringCase.svg)

**References:** [BooleanExpression](#booleanexpression), [StringCaseValue](#stringcasevalue)

### StringDefaultCase

![StringDefaultCase](TinyExpressionP4_StringDefaultCase.svg)

**References:** [StringCaseValue](#stringcasevalue)

### StringCaseValue

![StringCaseValue](TinyExpressionP4_StringCaseValue.svg)

**References:** [StringExpression](#stringexpression)

### BooleanMatchExpression

![BooleanMatchExpression](TinyExpressionP4_BooleanMatchExpression.svg)

**References:** [BooleanCase](#booleancase), [BooleanDefaultCase](#booleandefaultcase)

### BooleanCase

![BooleanCase](TinyExpressionP4_BooleanCase.svg)

**References:** [BooleanCaseValue](#booleancasevalue), [BooleanExpression](#booleanexpression)

### BooleanDefaultCase

![BooleanDefaultCase](TinyExpressionP4_BooleanDefaultCase.svg)

**References:** [BooleanCaseValue](#booleancasevalue)

### BooleanCaseValue

![BooleanCaseValue](TinyExpressionP4_BooleanCaseValue.svg)

**References:** [BooleanExpression](#booleanexpression)

### VariableRef

![VariableRef](TinyExpressionP4_VariableRef.svg)

**References:** [IDENTIFIER](#identifier), [Slice](#slice), [TypeHint](#typehint)

### Expression

![Expression](TinyExpressionP4_Expression.svg)

**References:** [BooleanExpression](#booleanexpression), [Expression](#expression), [MethodInvocation](#methodinvocation), [NumberExpression](#numberexpression), [ObjectExpression](#objectexpression), [StringExpression](#stringexpression)

