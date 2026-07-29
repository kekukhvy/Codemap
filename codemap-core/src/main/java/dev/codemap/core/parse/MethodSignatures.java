package dev.codemap.core.parse;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;

import java.util.stream.Collectors;

/**
 * Builds human-readable signatures and stable identifiers for methods.
 *
 * <p>Parameter types are part of both, because overloads must stay distinct. Two
 * {@code requireText(...)} declarations that collapsed to one entry would merge
 * their callers in the graph and mislead the reader about what calls what — the
 * exact confusion the symbol solver exists to prevent in M3.
 */
final class MethodSignatures {

    private static final String PARAMETER_SEPARATOR = ", ";
    private static final String ID_SEPARATOR = "#";
    private static final String VARARGS = "...";
    private static final String RETURN_SEPARATOR = " : ";

    /**
     * Renders a signature for display, e.g. {@code create(CreateTaskCommand) : Task}.
     *
     * <p>Types are kept in the form the author wrote them: an import-shortened
     * {@code Task} is what appears in the source and what a reader recognises.
     *
     * @param callable a method or constructor declaration
     * @return the signature, including parameter types and any return type
     */
    String of(CallableDeclaration<?> callable) {
        String parameters = callable.getParameters().stream()
                .map(this::renderParameter)
                .collect(Collectors.joining(PARAMETER_SEPARATOR));

        String signature = callable.getNameAsString() + "(" + parameters + ")";

        if (callable instanceof MethodDeclaration method) {
            return signature + RETURN_SEPARATOR + method.getType().asString();
        }
        return signature;
    }

    /**
     * Builds an index-wide unique identifier for a method.
     *
     * <p>Formed from the owning type and the parameter types, so overloads differ
     * and the id stays stable across runs as long as the declaration does. The
     * return type is excluded deliberately: Java does not overload on it, and
     * including it would churn ids when a return type is widened.
     *
     * @param classId owning type's identifier
     * @param callable a method or constructor declaration
     * @return a stable identifier
     */
    String methodId(String classId, CallableDeclaration<?> callable) {
        String parameterTypes = callable.getParameters().stream()
                .map(parameter -> parameter.getType().asString())
                .collect(Collectors.joining(PARAMETER_SEPARATOR));

        return classId + ID_SEPARATOR + callable.getNameAsString() + "(" + parameterTypes + ")";
    }

    private String renderParameter(Parameter parameter) {
        String type = parameter.getType().asString();
        return parameter.isVarArgs() ? type + VARARGS : type;
    }
}
