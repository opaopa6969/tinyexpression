package org.unlaxer.tinyexpression.p4.ubnfc.generated;

public sealed interface TinyExpressionP4AST permits TinyExpressionP4AST.FormulaExpr, TinyExpressionP4AST.CodeBlockExpr, TinyExpressionP4AST.ImportDeclarationExpr, TinyExpressionP4AST.QualifiedNameExpr, TinyExpressionP4AST.NumberVariableDeclarationExpr, TinyExpressionP4AST.StringVariableDeclarationExpr, TinyExpressionP4AST.BooleanVariableDeclarationExpr, TinyExpressionP4AST.ObjectVariableDeclarationExpr, TinyExpressionP4AST.OnlyIfAbsentExpr, TinyExpressionP4AST.NumberMethodDeclarationExpr, TinyExpressionP4AST.StringMethodDeclarationExpr, TinyExpressionP4AST.BooleanMethodDeclarationExpr, TinyExpressionP4AST.ObjectMethodDeclarationExpr, TinyExpressionP4AST.MethodParametersExpr, TinyExpressionP4AST.MethodParameterExpr, TinyExpressionP4AST.ExternalBooleanInvocationExpr, TinyExpressionP4AST.ExternalNumberInvocationExpr, TinyExpressionP4AST.ExternalStringInvocationExpr, TinyExpressionP4AST.ExternalObjectInvocationExpr, TinyExpressionP4AST.MethodInvocationExpr, TinyExpressionP4AST.TernaryExpr, TinyExpressionP4AST.ArgumentExpressionExpr, TinyExpressionP4AST.ArgumentsExpr, TinyExpressionP4AST.BinaryExpr, TinyExpressionP4AST.SinExpr, TinyExpressionP4AST.CosExpr, TinyExpressionP4AST.TanExpr, TinyExpressionP4AST.SqrtExpr, TinyExpressionP4AST.MinExpr, TinyExpressionP4AST.MaxExpr, TinyExpressionP4AST.RandomExpr, TinyExpressionP4AST.AbsExpr, TinyExpressionP4AST.RoundExpr, TinyExpressionP4AST.CeilExpr, TinyExpressionP4AST.FloorExpr, TinyExpressionP4AST.PowExpr, TinyExpressionP4AST.LogExpr, TinyExpressionP4AST.ExpExpr, TinyExpressionP4AST.ToNumExpr, TinyExpressionP4AST.ToUpperCaseExpr, TinyExpressionP4AST.ToLowerCaseExpr, TinyExpressionP4AST.TrimExpr, TinyExpressionP4AST.LengthExpr, TinyExpressionP4AST.ToUpperCaseDotExpr, TinyExpressionP4AST.ToLowerCaseDotExpr, TinyExpressionP4AST.TrimDotExpr, TinyExpressionP4AST.LengthDotExpr, TinyExpressionP4AST.StartsWithExpr, TinyExpressionP4AST.EndsWithExpr, TinyExpressionP4AST.ContainsExpr, TinyExpressionP4AST.InExpr, TinyExpressionP4AST.StartsWithDotExpr, TinyExpressionP4AST.EndsWithDotExpr, TinyExpressionP4AST.ContainsDotExpr, TinyExpressionP4AST.IsPresentExpr, TinyExpressionP4AST.InTimeRangeExpr, TinyExpressionP4AST.InDayTimeRangeExpr, TinyExpressionP4AST.SliceExpr, TinyExpressionP4AST.StringConcatExpr, TinyExpressionP4AST.StringCastVariableRefExpr, TinyExpressionP4AST.StringTypedVariableRefExpr, TinyExpressionP4AST.BooleanOrExpr, TinyExpressionP4AST.BooleanAndExpr, TinyExpressionP4AST.BooleanXorExpr, TinyExpressionP4AST.NotExpr, TinyExpressionP4AST.BooleanEqualityExpr, TinyExpressionP4AST.BooleanFactorExpr, TinyExpressionP4AST.StringComparisonExpr, TinyExpressionP4AST.ComparisonExpr, TinyExpressionP4AST.ObjectExpr, TinyExpressionP4AST.IfExpr, TinyExpressionP4AST.BranchExpressionExpr, TinyExpressionP4AST.NumberMatchExpr, TinyExpressionP4AST.NumberCaseExpr, TinyExpressionP4AST.NumberDefaultCaseExpr, TinyExpressionP4AST.NumberCaseValueExpr, TinyExpressionP4AST.StringMatchExpr, TinyExpressionP4AST.StringCaseExpr, TinyExpressionP4AST.StringDefaultCaseExpr, TinyExpressionP4AST.StringCaseValueExpr, TinyExpressionP4AST.BooleanMatchExpr, TinyExpressionP4AST.BooleanCaseExpr, TinyExpressionP4AST.BooleanDefaultCaseExpr, TinyExpressionP4AST.BooleanCaseValueExpr, TinyExpressionP4AST.VariableRefExpr, TinyExpressionP4AST.ExpressionExpr {
    record FormulaExpr(java.util.List<TinyExpressionP4AST.ImportDeclarationExpr> imports, java.util.List<java.lang.Object> declarations, TinyExpressionP4AST.ExpressionExpr expression, java.util.List<java.lang.Object> methods) implements TinyExpressionP4AST {
        public FormulaExpr {
            imports = java.util.List.copyOf(imports);
            declarations = java.util.List.copyOf(declarations);
            methods = java.util.List.copyOf(methods);
        }
    }
    record CodeBlockExpr() implements TinyExpressionP4AST {
    }
    record ImportDeclarationExpr(TinyExpressionP4AST.QualifiedNameExpr className, java.util.Optional<java.lang.String> method, java.lang.String alias) implements TinyExpressionP4AST {
        public ImportDeclarationExpr {
            java.util.Objects.requireNonNull(method);
        }
    }
    record QualifiedNameExpr(java.lang.String head, java.util.List<java.lang.String> tail) implements TinyExpressionP4AST {
        public QualifiedNameExpr {
            tail = java.util.List.copyOf(tail);
        }
    }
    record NumberVariableDeclarationExpr(java.lang.String varName, java.util.Optional<TinyExpressionP4AST.OnlyIfAbsentExpr> onlyIfAbsent, java.util.Optional<TinyExpressionP4AST.BinaryExpr> value, java.util.Optional<java.lang.String> desc) implements TinyExpressionP4AST {
        public NumberVariableDeclarationExpr {
            java.util.Objects.requireNonNull(onlyIfAbsent);
            java.util.Objects.requireNonNull(value);
            java.util.Objects.requireNonNull(desc);
        }
    }
    record StringVariableDeclarationExpr(java.lang.String varName, java.util.Optional<TinyExpressionP4AST.OnlyIfAbsentExpr> onlyIfAbsent, java.util.Optional<TinyExpressionP4AST.StringConcatExpr> value, java.util.Optional<java.lang.String> desc) implements TinyExpressionP4AST {
        public StringVariableDeclarationExpr {
            java.util.Objects.requireNonNull(onlyIfAbsent);
            java.util.Objects.requireNonNull(value);
            java.util.Objects.requireNonNull(desc);
        }
    }
    record BooleanVariableDeclarationExpr(java.lang.String varName, java.util.Optional<TinyExpressionP4AST.OnlyIfAbsentExpr> onlyIfAbsent, java.util.Optional<TinyExpressionP4AST.BooleanOrExpr> value, java.util.Optional<java.lang.String> desc) implements TinyExpressionP4AST {
        public BooleanVariableDeclarationExpr {
            java.util.Objects.requireNonNull(onlyIfAbsent);
            java.util.Objects.requireNonNull(value);
            java.util.Objects.requireNonNull(desc);
        }
    }
    record ObjectVariableDeclarationExpr(java.lang.String varName, java.util.Optional<TinyExpressionP4AST.OnlyIfAbsentExpr> onlyIfAbsent, java.util.Optional<TinyExpressionP4AST.ObjectExpr> value, java.util.Optional<java.lang.String> desc) implements TinyExpressionP4AST {
        public ObjectVariableDeclarationExpr {
            java.util.Objects.requireNonNull(onlyIfAbsent);
            java.util.Objects.requireNonNull(value);
            java.util.Objects.requireNonNull(desc);
        }
    }
    record OnlyIfAbsentExpr() implements TinyExpressionP4AST {
    }
    record NumberMethodDeclarationExpr(java.lang.String methodName, java.util.Optional<TinyExpressionP4AST.MethodParametersExpr> parameters, TinyExpressionP4AST.BinaryExpr expression) implements TinyExpressionP4AST {
        public NumberMethodDeclarationExpr {
            java.util.Objects.requireNonNull(parameters);
        }
    }
    record StringMethodDeclarationExpr(java.lang.String methodName, java.util.Optional<TinyExpressionP4AST.MethodParametersExpr> parameters, TinyExpressionP4AST.StringConcatExpr expression) implements TinyExpressionP4AST {
        public StringMethodDeclarationExpr {
            java.util.Objects.requireNonNull(parameters);
        }
    }
    record BooleanMethodDeclarationExpr(java.lang.String methodName, java.util.Optional<TinyExpressionP4AST.MethodParametersExpr> parameters, TinyExpressionP4AST.BooleanOrExpr expression) implements TinyExpressionP4AST {
        public BooleanMethodDeclarationExpr {
            java.util.Objects.requireNonNull(parameters);
        }
    }
    record ObjectMethodDeclarationExpr(java.lang.String methodName, java.util.Optional<TinyExpressionP4AST.MethodParametersExpr> parameters, TinyExpressionP4AST.ObjectExpr expression) implements TinyExpressionP4AST {
        public ObjectMethodDeclarationExpr {
            java.util.Objects.requireNonNull(parameters);
        }
    }
    record MethodParametersExpr(java.util.List<TinyExpressionP4AST.MethodParameterExpr> values) implements TinyExpressionP4AST {
        public MethodParametersExpr {
            values = java.util.List.copyOf(values);
        }
    }
    record MethodParameterExpr(java.lang.String paramName, java.util.Optional<java.lang.String> type) implements TinyExpressionP4AST {
        public MethodParameterExpr {
            java.util.Objects.requireNonNull(type);
        }
    }
    record ExternalBooleanInvocationExpr(java.util.Optional<TinyExpressionP4AST.QualifiedNameExpr> className, java.lang.String name, java.util.Optional<TinyExpressionP4AST.ArgumentsExpr> args) implements TinyExpressionP4AST {
        public ExternalBooleanInvocationExpr {
            java.util.Objects.requireNonNull(className);
            java.util.Objects.requireNonNull(args);
        }
    }
    record ExternalNumberInvocationExpr(java.util.Optional<TinyExpressionP4AST.QualifiedNameExpr> className, java.lang.String name, java.util.Optional<TinyExpressionP4AST.ArgumentsExpr> args) implements TinyExpressionP4AST {
        public ExternalNumberInvocationExpr {
            java.util.Objects.requireNonNull(className);
            java.util.Objects.requireNonNull(args);
        }
    }
    record ExternalStringInvocationExpr(java.util.Optional<TinyExpressionP4AST.QualifiedNameExpr> className, java.lang.String name, java.util.Optional<TinyExpressionP4AST.ArgumentsExpr> args) implements TinyExpressionP4AST {
        public ExternalStringInvocationExpr {
            java.util.Objects.requireNonNull(className);
            java.util.Objects.requireNonNull(args);
        }
    }
    record ExternalObjectInvocationExpr(java.util.Optional<TinyExpressionP4AST.QualifiedNameExpr> className, java.lang.String name, java.util.Optional<TinyExpressionP4AST.ArgumentsExpr> args) implements TinyExpressionP4AST {
        public ExternalObjectInvocationExpr {
            java.util.Objects.requireNonNull(className);
            java.util.Objects.requireNonNull(args);
        }
    }
    record MethodInvocationExpr(java.lang.String name, java.util.Optional<TinyExpressionP4AST.ArgumentsExpr> args) implements TinyExpressionP4AST {
        public MethodInvocationExpr {
            java.util.Objects.requireNonNull(args);
        }
    }
    record TernaryExpr(TinyExpressionP4AST.BooleanOrExpr condition, TinyExpressionP4AST.BranchExpressionExpr thenExpr, TinyExpressionP4AST.BranchExpressionExpr elseExpr) implements TinyExpressionP4AST {
    }
    record ArgumentExpressionExpr(java.lang.Object value) implements TinyExpressionP4AST {
    }
    record ArgumentsExpr(java.util.List<TinyExpressionP4AST.ArgumentExpressionExpr> values) implements TinyExpressionP4AST {
        public ArgumentsExpr {
            values = java.util.List.copyOf(values);
        }
    }
    record BinaryExpr(TinyExpressionP4AST left, java.util.List<java.lang.String> op, java.util.List<TinyExpressionP4AST> right) implements TinyExpressionP4AST {
        public BinaryExpr {
            op = java.util.List.copyOf(op);
            right = java.util.List.copyOf(right);
        }
    }
    record SinExpr(TinyExpressionP4AST.ArgumentExpressionExpr arg) implements TinyExpressionP4AST {
    }
    record CosExpr(TinyExpressionP4AST.ArgumentExpressionExpr arg) implements TinyExpressionP4AST {
    }
    record TanExpr(TinyExpressionP4AST.ArgumentExpressionExpr arg) implements TinyExpressionP4AST {
    }
    record SqrtExpr(TinyExpressionP4AST.ArgumentExpressionExpr arg) implements TinyExpressionP4AST {
    }
    record MinExpr(TinyExpressionP4AST.ArgumentExpressionExpr first, java.util.List<TinyExpressionP4AST.ArgumentExpressionExpr> rest) implements TinyExpressionP4AST {
        public MinExpr {
            rest = java.util.List.copyOf(rest);
        }
    }
    record MaxExpr(TinyExpressionP4AST.ArgumentExpressionExpr first, java.util.List<TinyExpressionP4AST.ArgumentExpressionExpr> rest) implements TinyExpressionP4AST {
        public MaxExpr {
            rest = java.util.List.copyOf(rest);
        }
    }
    record RandomExpr() implements TinyExpressionP4AST {
    }
    record AbsExpr(TinyExpressionP4AST.ArgumentExpressionExpr arg) implements TinyExpressionP4AST {
    }
    record RoundExpr(TinyExpressionP4AST.ArgumentExpressionExpr arg) implements TinyExpressionP4AST {
    }
    record CeilExpr(TinyExpressionP4AST.ArgumentExpressionExpr arg) implements TinyExpressionP4AST {
    }
    record FloorExpr(TinyExpressionP4AST.ArgumentExpressionExpr arg) implements TinyExpressionP4AST {
    }
    record PowExpr(TinyExpressionP4AST.ArgumentExpressionExpr base, TinyExpressionP4AST.ArgumentExpressionExpr exponent) implements TinyExpressionP4AST {
    }
    record LogExpr(TinyExpressionP4AST.ArgumentExpressionExpr arg) implements TinyExpressionP4AST {
    }
    record ExpExpr(TinyExpressionP4AST.ArgumentExpressionExpr arg) implements TinyExpressionP4AST {
    }
    record ToNumExpr(TinyExpressionP4AST.StringConcatExpr value, TinyExpressionP4AST.ArgumentExpressionExpr defaultValue) implements TinyExpressionP4AST {
    }
    record ToUpperCaseExpr(TinyExpressionP4AST.StringConcatExpr value) implements TinyExpressionP4AST {
    }
    record ToLowerCaseExpr(TinyExpressionP4AST.StringConcatExpr value) implements TinyExpressionP4AST {
    }
    record TrimExpr(TinyExpressionP4AST.StringConcatExpr value) implements TinyExpressionP4AST {
    }
    record LengthExpr(TinyExpressionP4AST.StringConcatExpr value) implements TinyExpressionP4AST {
    }
    record ToUpperCaseDotExpr(TinyExpressionP4AST.VariableRefExpr value) implements TinyExpressionP4AST {
    }
    record ToLowerCaseDotExpr(TinyExpressionP4AST.VariableRefExpr value) implements TinyExpressionP4AST {
    }
    record TrimDotExpr(TinyExpressionP4AST.VariableRefExpr value) implements TinyExpressionP4AST {
    }
    record LengthDotExpr(TinyExpressionP4AST.VariableRefExpr value) implements TinyExpressionP4AST {
    }
    record StartsWithExpr(TinyExpressionP4AST.StringConcatExpr value, java.util.List<TinyExpressionP4AST.StringConcatExpr> patterns) implements TinyExpressionP4AST {
        public StartsWithExpr {
            patterns = java.util.List.copyOf(patterns);
        }
    }
    record EndsWithExpr(TinyExpressionP4AST.StringConcatExpr value, java.util.List<TinyExpressionP4AST.StringConcatExpr> patterns) implements TinyExpressionP4AST {
        public EndsWithExpr {
            patterns = java.util.List.copyOf(patterns);
        }
    }
    record ContainsExpr(TinyExpressionP4AST.StringConcatExpr value, java.util.List<TinyExpressionP4AST.StringConcatExpr> patterns) implements TinyExpressionP4AST {
        public ContainsExpr {
            patterns = java.util.List.copyOf(patterns);
        }
    }
    record InExpr(TinyExpressionP4AST.StringConcatExpr value, java.util.List<TinyExpressionP4AST.StringConcatExpr> candidates) implements TinyExpressionP4AST {
        public InExpr {
            candidates = java.util.List.copyOf(candidates);
        }
    }
    record StartsWithDotExpr(java.lang.Object value, java.util.List<TinyExpressionP4AST.StringConcatExpr> patterns) implements TinyExpressionP4AST {
        public StartsWithDotExpr {
            patterns = java.util.List.copyOf(patterns);
        }
    }
    record EndsWithDotExpr(java.lang.Object value, java.util.List<TinyExpressionP4AST.StringConcatExpr> patterns) implements TinyExpressionP4AST {
        public EndsWithDotExpr {
            patterns = java.util.List.copyOf(patterns);
        }
    }
    record ContainsDotExpr(java.lang.Object value, java.util.List<TinyExpressionP4AST.StringConcatExpr> patterns) implements TinyExpressionP4AST {
        public ContainsDotExpr {
            patterns = java.util.List.copyOf(patterns);
        }
    }
    record IsPresentExpr(TinyExpressionP4AST.VariableRefExpr value) implements TinyExpressionP4AST {
    }
    record InTimeRangeExpr(TinyExpressionP4AST.BinaryExpr startHour, TinyExpressionP4AST.BinaryExpr endHour) implements TinyExpressionP4AST {
    }
    record InDayTimeRangeExpr(java.lang.String startDay, TinyExpressionP4AST.BinaryExpr startHour, java.lang.String endDay, TinyExpressionP4AST.BinaryExpr endHour) implements TinyExpressionP4AST {
    }
    record SliceExpr(java.lang.Object value, java.util.Optional<TinyExpressionP4AST.BinaryExpr> start, java.util.Optional<TinyExpressionP4AST.BinaryExpr> end, java.util.Optional<TinyExpressionP4AST.BinaryExpr> step) implements TinyExpressionP4AST {
        public SliceExpr {
            java.util.Objects.requireNonNull(start);
            java.util.Objects.requireNonNull(end);
            java.util.Objects.requireNonNull(step);
        }
    }
    record StringConcatExpr(java.lang.Object left, java.util.List<java.lang.String> op, java.util.List<java.lang.Object> right) implements TinyExpressionP4AST {
        public StringConcatExpr {
            op = java.util.List.copyOf(op);
            right = java.util.List.copyOf(right);
        }
    }
    record StringCastVariableRefExpr(java.lang.String name) implements TinyExpressionP4AST {
    }
    record StringTypedVariableRefExpr(java.lang.String name) implements TinyExpressionP4AST {
    }
    record BooleanOrExpr(TinyExpressionP4AST.BooleanAndExpr left, java.util.List<java.lang.String> op, java.util.List<TinyExpressionP4AST.BooleanAndExpr> right) implements TinyExpressionP4AST {
        public BooleanOrExpr {
            op = java.util.List.copyOf(op);
            right = java.util.List.copyOf(right);
        }
    }
    record BooleanAndExpr(TinyExpressionP4AST.BooleanXorExpr left, java.util.List<java.lang.String> op, java.util.List<TinyExpressionP4AST.BooleanXorExpr> right) implements TinyExpressionP4AST {
        public BooleanAndExpr {
            op = java.util.List.copyOf(op);
            right = java.util.List.copyOf(right);
        }
    }
    record BooleanXorExpr(TinyExpressionP4AST.BooleanFactorExpr left, java.util.List<java.lang.String> op, java.util.List<TinyExpressionP4AST.BooleanFactorExpr> right) implements TinyExpressionP4AST {
        public BooleanXorExpr {
            op = java.util.List.copyOf(op);
            right = java.util.List.copyOf(right);
        }
    }
    record NotExpr(TinyExpressionP4AST.BooleanOrExpr value) implements TinyExpressionP4AST {
    }
    record BooleanEqualityExpr(java.lang.Object left, java.lang.String op, java.lang.Object right) implements TinyExpressionP4AST {
    }
    record BooleanFactorExpr(java.lang.Object value) implements TinyExpressionP4AST {
    }
    record StringComparisonExpr(TinyExpressionP4AST.StringConcatExpr left, java.lang.String op, TinyExpressionP4AST.StringConcatExpr right) implements TinyExpressionP4AST {
    }
    record ComparisonExpr(TinyExpressionP4AST.BinaryExpr left, java.lang.String op, TinyExpressionP4AST.BinaryExpr right) implements TinyExpressionP4AST {
    }
    record ObjectExpr(java.lang.Object value) implements TinyExpressionP4AST {
    }
    record IfExpr(TinyExpressionP4AST.BooleanOrExpr condition, TinyExpressionP4AST.BranchExpressionExpr thenExpr, TinyExpressionP4AST.BranchExpressionExpr elseExpr) implements TinyExpressionP4AST {
    }
    record BranchExpressionExpr(java.lang.Object value) implements TinyExpressionP4AST {
    }
    record NumberMatchExpr(TinyExpressionP4AST.NumberCaseExpr firstCase, java.util.List<TinyExpressionP4AST.NumberCaseExpr> moreCases, TinyExpressionP4AST.NumberDefaultCaseExpr defaultCase) implements TinyExpressionP4AST {
        public NumberMatchExpr {
            moreCases = java.util.List.copyOf(moreCases);
        }
    }
    record NumberCaseExpr(TinyExpressionP4AST.BooleanOrExpr condition, TinyExpressionP4AST.NumberCaseValueExpr value) implements TinyExpressionP4AST {
    }
    record NumberDefaultCaseExpr(TinyExpressionP4AST.NumberCaseValueExpr value) implements TinyExpressionP4AST {
    }
    record NumberCaseValueExpr(TinyExpressionP4AST.BinaryExpr value) implements TinyExpressionP4AST {
    }
    record StringMatchExpr(TinyExpressionP4AST.StringCaseExpr firstCase, java.util.List<TinyExpressionP4AST.StringCaseExpr> moreCases, TinyExpressionP4AST.StringDefaultCaseExpr defaultCase) implements TinyExpressionP4AST {
        public StringMatchExpr {
            moreCases = java.util.List.copyOf(moreCases);
        }
    }
    record StringCaseExpr(TinyExpressionP4AST.BooleanOrExpr condition, TinyExpressionP4AST.StringCaseValueExpr value) implements TinyExpressionP4AST {
    }
    record StringDefaultCaseExpr(TinyExpressionP4AST.StringCaseValueExpr value) implements TinyExpressionP4AST {
    }
    record StringCaseValueExpr(TinyExpressionP4AST.StringConcatExpr value) implements TinyExpressionP4AST {
    }
    record BooleanMatchExpr(TinyExpressionP4AST.BooleanCaseExpr firstCase, java.util.List<TinyExpressionP4AST.BooleanCaseExpr> moreCases, TinyExpressionP4AST.BooleanDefaultCaseExpr defaultCase) implements TinyExpressionP4AST {
        public BooleanMatchExpr {
            moreCases = java.util.List.copyOf(moreCases);
        }
    }
    record BooleanCaseExpr(TinyExpressionP4AST.BooleanOrExpr condition, TinyExpressionP4AST.BooleanCaseValueExpr value) implements TinyExpressionP4AST {
    }
    record BooleanDefaultCaseExpr(TinyExpressionP4AST.BooleanCaseValueExpr value) implements TinyExpressionP4AST {
    }
    record BooleanCaseValueExpr(TinyExpressionP4AST.BooleanOrExpr value) implements TinyExpressionP4AST {
    }
    record VariableRefExpr(java.lang.String name, java.util.Optional<java.lang.String> type) implements TinyExpressionP4AST {
        public VariableRefExpr {
            java.util.Objects.requireNonNull(type);
        }
    }
    record ExpressionExpr(java.lang.Object value) implements TinyExpressionP4AST {
    }
}
