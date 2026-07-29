package dev.codemap.core.entrypoint;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Resolves an expression to a literal string, following {@code private static
 * final String} field references across files when the value is statically
 * determinable (spec §4.1).
 *
 * <p>Path constants in the reference project live in a different class than the
 * annotation or call that uses them ({@code @Route(value = ScheduleRoutes.SCHEDULES)}),
 * so this must resolve through the symbol solver rather than only reading the
 * current file's own fields. When resolution fails — the value comes from a method
 * call, a runtime computation, or a field the solver cannot reach — the caller
 * falls back to the expression's source text rather than dropping the entry point.
 */
final class ConstantFolder {

    private static final Logger log = LoggerFactory.getLogger(ConstantFolder.class);

    /**
     * Folds an expression to its literal string value.
     *
     * @param expression the annotation member value or call argument to resolve
     * @return the literal value, or empty when it is not a statically determinable string
     */
    Optional<String> fold(Expression expression) {
        if (expression.isStringLiteralExpr()) {
            return Optional.of(expression.asStringLiteralExpr().asString());
        }
        if (expression.isNameExpr()) {
            return foldFieldReference(expression.asNameExpr());
        }
        if (expression.isFieldAccessExpr()) {
            return foldFieldReference(expression.asFieldAccessExpr());
        }
        return Optional.empty();
    }

    /**
     * Renders the expression as written, for callers that must not drop an entry
     * point just because its path could not be folded to a literal.
     */
    String textOf(Expression expression) {
        return expression.toString();
    }

    private Optional<String> foldFieldReference(NameExpr reference) {
        try {
            return literalInitializer(reference.resolve());
        } catch (RuntimeException e) {
            log.debug("Could not resolve field reference {}: {}", reference, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> foldFieldReference(FieldAccessExpr reference) {
        try {
            return literalInitializer(reference.resolve());
        } catch (RuntimeException e) {
            log.debug("Could not resolve field reference {}: {}", reference, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads a resolved field declaration's own initializer, if it is itself a literal.
     *
     * <p>{@code ResolvedFieldDeclaration.toAst()} associates to the enclosing
     * {@link FieldDeclaration} node — which may declare several variables on one
     * line — rather than directly to the one {@link VariableDeclarator} that was
     * referenced, so the matching declarator is picked out by name.
     */
    private Optional<String> literalInitializer(ResolvedValueDeclaration resolved) {
        if (!resolved.isField()) {
            return Optional.empty();
        }
        ResolvedFieldDeclaration field = resolved.asField();
        return field.toAst(FieldDeclaration.class)
                .flatMap(declaration -> declaratorNamed(declaration, field.getName()))
                .flatMap(VariableDeclarator::getInitializer)
                .flatMap(this::fold);
    }

    private Optional<VariableDeclarator> declaratorNamed(FieldDeclaration declaration, String name) {
        return declaration.getVariables().stream()
                .filter(variable -> variable.getNameAsString().equals(name))
                .findFirst();
    }
}
