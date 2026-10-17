//! Explicit family projection of a single successful recognition. No parser entry calls.
use crate::generated::ast::{Ast, AstValue};
use crate::Span;
use std::cell::Cell;

pub(crate) fn project(ast: &Ast, family: &str, source: &str) -> Result<Ast, ()> {
    match ast {
        Ast::FormulaExpr { expression, .. } => return project(expression, family, source),
        Ast::ExpressionExpr { value, .. } => return project(value, family, source),
        _ => {}
    }
    if family == "OBJECT" {
        return Ok(match ast {
            Ast::ObjectExpr { .. } => ast.clone(),
            _ => Ast::ObjectExpr {
                span: ast.span(),
                value: Box::new(ast.clone()),
            },
        });
    }
    if matches!(ast, Ast::StringMatchExpr { .. }) {
        return if family == "STRING" {
            Ok(ast.clone())
        } else {
            Err(())
        };
    }
    if matches!(ast, Ast::BooleanMatchExpr { .. }) {
        return if family == "BOOLEAN" {
            Ok(ast.clone())
        } else {
            Err(())
        };
    }
    if family == "NUMBER" {
        return Ok(ast.clone());
    }
    let layout: String = crate::runtime::select::strip_comments(source)
        .into_iter()
        .collect();
    let projection = Projection::new(&layout);
    if let Ast::NumberMatchExpr { .. } = ast {
        return projection.match_node(ast, family);
    }
    if family == "STRING" {
        projection.string_expression(ast)
    } else {
        projection.boolean_expression(ast)
    }
}

pub(crate) fn with_root_span(mut ast: Ast, root_span: Span) -> Ast {
    match &mut ast {
        Ast::StringConcatExpr { span, .. }
        | Ast::BooleanOrExpr { span, .. }
        | Ast::ObjectExpr { span, .. }
        | Ast::NumberMatchExpr { span, .. }
        | Ast::StringMatchExpr { span, .. }
        | Ast::BooleanMatchExpr { span, .. } => *span = root_span,
        _ => {}
    }
    ast
}

struct Projection<'a> {
    source: &'a str,
    cursor: Cell<(usize, usize)>,
}
impl<'a> Projection<'a> {
    fn new(source: &'a str) -> Self {
        Self {
            source,
            cursor: Cell::new((0, 0)),
        }
    }

    fn seek(&self, target: usize) -> Result<usize, ()> {
        let (mut cp, mut byte) = self.cursor.get();
        while cp < target {
            byte += self
                .source
                .get(byte..)
                .and_then(|s| s.chars().next())
                .ok_or(())?
                .len_utf8();
            cp += 1;
        }
        while cp > target {
            byte -= self
                .source
                .get(..byte)
                .and_then(|s| s.chars().next_back())
                .ok_or(())?
                .len_utf8();
            cp -= 1;
        }
        self.cursor.set((cp, byte));
        Ok(byte)
    }

    fn text(&self, span: Span) -> Result<&'a str, ()> {
        if span.end < span.start {
            return Err(());
        }
        let start = self.seek(span.start)?;
        let end = self.seek(span.end)?;
        self.source.get(start..end).ok_or(())
    }

    fn string_expression(&self, ast: &Ast) -> Result<Ast, ()> {
        let span = ast.span();
        match ast {
            Ast::StringConcatExpr { .. } => Ok(ast.clone()),
            Ast::BinaryExpr {
                left, op, right, ..
            } => {
                if op.iter().any(|op| op != "+") {
                    return Err(());
                }
                Ok(Ast::StringConcatExpr {
                    span,
                    left: self.string_term(left)?,
                    op: op.clone(),
                    right: right
                        .iter()
                        .map(|value| self.string_term(value))
                        .collect::<Result<_, _>>()?,
                })
            }
            _ => Ok(Ast::StringConcatExpr {
                span,
                left: self.string_atom(&AstValue::Node(Box::new(ast.clone())))?,
                op: Vec::new(),
                right: Vec::new(),
            }),
        }
    }

    fn string_term(&self, value: &AstValue) -> Result<AstValue, ()> {
        if let AstValue::Node(ast) = value {
            if let Ast::BinaryExpr { left, op, .. } = ast.as_ref() {
                if !op.is_empty() {
                    return Err(());
                }
                if let AstValue::Node(nested) = left {
                    if matches!(nested.as_ref(), Ast::BinaryExpr { .. }) {
                        return Ok(AstValue::Node(Box::new(self.string_expression(nested)?)));
                    }
                }
                return self.string_atom(left);
            }
        }
        self.string_atom(value)
    }

    fn string_atom(&self, value: &AstValue) -> Result<AstValue, ()> {
        let AstValue::Node(ast) = value else {
            return Err(());
        };
        let result = match ast.as_ref() {
            Ast::VariableRefExpr { span, name, r#type } => {
                let text = self.text(*span)?;
                let body = text.trim().strip_prefix('$').ok_or(())?.trim_start();
                // IDENTIFIER is ASCII in this grammar; byte and CP name lengths agree.
                let tail = body.get(name.len()..).ok_or(())?;
                if tail.trim().starts_with("as")
                    && r#type
                        .as_ref()
                        .is_some_and(|v| v.eq_ignore_ascii_case("string"))
                {
                    Ast::StringTypedVariableRefExpr {
                        span: *span,
                        name: name.clone(),
                    }
                } else {
                    *ast.clone()
                }
            }
            Ast::StringTypedVariableRefExpr { .. }
            | Ast::StringCastVariableRefExpr { .. }
            | Ast::MethodInvocationExpr { .. }
            | Ast::IfExpr { .. } => *ast.clone(),
            Ast::NumberMatchExpr { .. } => self.match_node(ast, "STRING")?,
            _ => return Err(()),
        };
        Ok(AstValue::Node(Box::new(result)))
    }

    fn boolean_expression(&self, ast: &Ast) -> Result<Ast, ()> {
        if matches!(ast, Ast::BooleanOrExpr { .. }) {
            return Ok(ast.clone());
        }
        let span = ast.span();
        let mut atom = ast;
        let mut nested_projection = None;
        let mut factor_span = span;
        if let Ast::BinaryExpr { left, op, .. } = ast {
            if !op.is_empty() {
                return Err(());
            }
            let AstValue::Node(node) = left else {
                return Err(());
            };
            atom = node;
            if let Ast::BinaryExpr {
                left,
                op,
                span: term_span,
                ..
            } = atom
            {
                if !op.is_empty() {
                    return Err(());
                }
                factor_span = *term_span;
                let AstValue::Node(node) = left else {
                    return Err(());
                };
                atom = node;
                if matches!(atom, Ast::BinaryExpr { .. }) {
                    nested_projection = Some(self.boolean_expression(atom)?);
                }
            }
        }
        if let Some(ref nested) = nested_projection {
            atom = nested;
        }
        let atom = match atom {
            Ast::NumberMatchExpr { .. } => self.match_node(atom, "BOOLEAN")?,
            Ast::VariableRefExpr { .. }
            | Ast::MethodInvocationExpr { .. }
            | Ast::IfExpr { .. }
            | Ast::BooleanMatchExpr { .. }
            | Ast::BooleanOrExpr { .. } => atom.clone(),
            _ => return Err(()),
        };
        Ok(Ast::BooleanOrExpr {
            span,
            op: Vec::new(),
            right: Vec::new(),
            left: Box::new(Ast::BooleanAndExpr {
                span,
                op: Vec::new(),
                right: Vec::new(),
                left: Box::new(Ast::BooleanXorExpr {
                    span,
                    op: Vec::new(),
                    right: Vec::new(),
                    left: Box::new(Ast::BooleanFactorExpr {
                        span: factor_span,
                        value: AstValue::Node(Box::new(atom)),
                    }),
                }),
            }),
        })
    }

    fn case_value(&self, ast: &Ast, family: &str) -> Result<Ast, ()> {
        let Ast::NumberCaseValueExpr { span, value } = ast else {
            return Err(());
        };
        if family == "STRING" {
            Ok(Ast::StringCaseValueExpr {
                span: *span,
                value: Box::new(self.string_expression(value)?),
            })
        } else {
            Ok(Ast::BooleanCaseValueExpr {
                span: *span,
                value: Box::new(self.boolean_expression(value)?),
            })
        }
    }

    fn case(&self, ast: &Ast, family: &str) -> Result<Ast, ()> {
        let Ast::NumberCaseExpr {
            span,
            condition,
            value,
        } = ast
        else {
            return Err(());
        };
        let value = Box::new(self.case_value(value, family)?);
        if family == "STRING" {
            Ok(Ast::StringCaseExpr {
                span: *span,
                condition: condition.clone(),
                value,
            })
        } else {
            Ok(Ast::BooleanCaseExpr {
                span: *span,
                condition: condition.clone(),
                value,
            })
        }
    }

    fn match_node(&self, ast: &Ast, family: &str) -> Result<Ast, ()> {
        let Ast::NumberMatchExpr {
            span,
            firstCase,
            moreCases,
            defaultCase,
        } = ast
        else {
            return Err(());
        };
        let first = Box::new(self.case(firstCase, family)?);
        let more = moreCases
            .iter()
            .map(|case| self.case(case, family))
            .collect::<Result<_, _>>()?;
        let Ast::NumberDefaultCaseExpr {
            span: default_span,
            value,
        } = defaultCase.as_ref()
        else {
            return Err(());
        };
        let default_value = Box::new(self.case_value(value, family)?);
        if family == "STRING" {
            Ok(Ast::StringMatchExpr {
                span: *span,
                firstCase: first,
                moreCases: more,
                defaultCase: Box::new(Ast::StringDefaultCaseExpr {
                    span: *default_span,
                    value: default_value,
                }),
            })
        } else {
            Ok(Ast::BooleanMatchExpr {
                span: *span,
                firstCase: first,
                moreCases: more,
                defaultCase: Box::new(Ast::BooleanDefaultCaseExpr {
                    span: *default_span,
                    value: default_value,
                }),
            })
        }
    }
}

#[cfg(test)]
mod tests {
    use super::Projection;
    use crate::Span;

    #[test]
    fn projection_reader_handles_reverse_repeated_and_unicode_spans() {
        let reader = Projection::new("a😀e\u{301}👨‍👩‍👧‍👦z");
        for (start, end, expected) in [
            (1, 2, "😀"),
            (4, 11, "👨‍👩‍👧‍👦"),
            (0, 1, "a"),
            (2, 4, "e\u{301}"),
            (1, 2, "😀"),
            (1, 2, "😀"),
            (12, 12, ""),
        ] {
            assert_eq!(reader.text(Span { start, end }).unwrap(), expected);
        }
        assert!(reader.text(Span { start: 4, end: 3 }).is_err());
        assert!(reader.text(Span { start: 12, end: 13 }).is_err());
        assert_eq!(reader.text(Span { start: 0, end: 1 }).unwrap(), "a");
    }
}
