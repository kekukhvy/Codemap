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

    /** A dotted package qualifier preceding a type name, e.g. the {@code java.util.} in {@code java.util.List}. */
    private static final java.util.regex.Pattern QUALIFIED_NAME =
            java.util.regex.Pattern.compile("\\b(?:[a-z][a-zA-Z0-9_]*\\.)+(?=[A-Z])");

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
            return signature + RETURN_SEPARATOR + simpleTypeName(method.getType().asString());
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
                .map(parameter -> simpleTypeName(parameter.getType().asString()))
                .collect(Collectors.joining(PARAMETER_SEPARATOR));

        return classId + ID_SEPARATOR + callable.getNameAsString() + "(" + parameterTypes + ")";
    }

    private String renderParameter(Parameter parameter) {
        String type = simpleTypeName(parameter.getType().asString());
        return parameter.isVarArgs() ? type + VARARGS : type;
    }

    /**
     * Strips package qualifiers from a type, keeping generic arguments intact.
     *
     * <p>{@code java.util.List<java.lang.String>} becomes {@code List<String>}.
     * Most code refers to imported types by their simple name, so normalising the
     * rare fully-qualified declaration keeps signatures consistent: the same
     * method reads the same way wherever it appears, and two overloads cannot look
     * different purely because their authors wrote imports differently.
     *
     * @param type type as written in the source
     * @return the type with every package qualifier removed
     */
    private static String simpleTypeName(String type) {
        return QUALIFIED_NAME.matcher(type).replaceAll("");
    }
}
