# TinyExpression P4 Railroad (Mermaid)

Source of truth:
- [UBNF draft](../ubnf/tinyexpression-p4-draft.ubnf)
- [BNF view](tinyexpression-p4-draft.bnf)

Notes:
- This file provides railroad-like diagrams in Mermaid for key rules.
- For complete rule details, use the BNF/UBNF files above.

## 1. Formula

```mermaid
flowchart LR
  F["Formula"] --> Z1["VariableDeclaration zero or more"]
  Z1 --> Z2["Annotation zero or more"]
  Z2 --> E["Expression"]
  E --> Z3["MethodDeclaration zero or more"]
```

## 2. Expression Choice

```mermaid
flowchart LR
  E["Expression"] --> N["NumberExpression"]
  E --> S["StringExpression"]
  E --> B["BooleanExpression"]
  E --> O["ObjectExpression"]
  E --> M["MethodInvocation"]
  E --> P["Parenthesized Expression"]
```

## 3. Number Expression (precedence)

```mermaid
flowchart LR
  NE["NumberExpression"] --> NT["NumberTerm"]
  NT --> REP1["Repeat AddOp and NumberTerm"]
  NT --> NF["NumberFactor"]
  NF --> REP2["Repeat MulOp and NumberFactor"]
```

## 4. Variable Declaration

```mermaid
flowchart LR
  VD["VariableDeclaration"] --> NVD["NumberVariableDeclaration"]
  VD --> SVD["StringVariableDeclaration"]
  VD --> BVD["BooleanVariableDeclaration"]
  VD --> OVD["ObjectVariableDeclaration"]

  NVD --> KW["Keyword var or variable"]
  KW --> DOLLAR["Dollar symbol"]
  DOLLAR --> ID["Identifier"]
  ID --> TH["Optional NumberTypeHint"]
  TH --> ST["Optional NumberSetter"]
  ST --> DESC["Description with String"]
  DESC --> SEMI["Statement Terminator"]
```

## 5. Method Declaration + Invocation

```mermaid
flowchart LR
  MD["MethodDeclaration"] --> NMD["NumberMethodDeclaration"]
  MD --> SMD["StringMethodDeclaration"]
  MD --> BMD["BooleanMethodDeclaration"]
  MD --> OMD["ObjectMethodDeclaration"]

  MI["MethodInvocation"] --> CALL["Keyword call"]
  CALL --> NAME["Identifier"]
  NAME --> LP["Left Parenthesis"]
  LP --> ARGS["Optional Arguments"]
  ARGS --> RP["Right Parenthesis"]
```

## 6. If / Match

```mermaid
flowchart LR
  IF["IfExpression"] --> IFKW["Keyword if"]
  IFKW --> COND["BooleanExpression"]
  COND --> THEN["Then Expression Block"]
  THEN --> ELSEKW["Keyword else"]
  ELSEKW --> ELSEB["Else Expression Block"]

  NM["NumberMatchExpression"] --> MKW["Keyword match"]
  MKW --> LCB["Left Brace"]
  LCB --> CASE1["NumberCase"]
  CASE1 --> CASEN["More NumberCase"]
  CASEN --> DEF["Default NumberCaseValue"]
  DEF --> RCB["Right Brace"]
```
