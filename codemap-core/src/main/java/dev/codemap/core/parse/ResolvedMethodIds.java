package dev.codemap.core.parse;

import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Builds method ids from symbol-solver declarations, in the exact format
 * {@link MethodSignatures} produces from an AST declaration.
 *
 * <p>The call graph resolves a call expression to a {@code ResolvedMethodLikeDeclaration}
 * rather than to the {@code CallableDeclaration} {@link MethodSignatures} was
 * written for — a call into another file, or into a library, has no AST node in
 * the current compilation unit. Both paths must agree on the id format, or a
 * resolved call could never join back to the method it targets.
 */
public final class ResolvedMethodIds {

    private static final String ID_SEPARATOR = "#";
    private static final String PARAMETER_SEPARATOR = ", ";
    private static final String VARARGS = "...";
    private static final String ARRAY_SUFFIX = "[]";

    private ResolvedMethodIds() {
    }

    /**
     * Builds the id of a resolved method or constructor declaration.
     *
     * @param declaration the resolved target of a call expression
     * @return an id matching the one {@link MethodSignatures#methodId} would have
     *         produced from the declaration site
     */
    public static String idOf(ResolvedMethodLikeDeclaration declaration) {
        String classId = declaration.declaringType().getQualifiedName();
        String parameterTypes = IntStream.range(0, declaration.getNumberOfParams())
                .mapToObj(declaration::getParam)
                .map(ResolvedMethodIds::renderParameterType)
                .collect(Collectors.joining(PARAMETER_SEPARATOR));

        return classId + ID_SEPARATOR + declaration.getName() + "(" + parameterTypes + ")";
    }

    /** The class id a resolved method or constructor belongs to. */
    public static String classIdOf(ResolvedMethodLikeDeclaration declaration) {
        return declaration.declaringType().getQualifiedName();
    }

    /**
     * Renders one parameter type exactly as the declaration site would.
     *
     * <p>Two normalisations are needed for the ids to match, and both were found by
     * edges failing to join. The solver may describe a variadic parameter either as
     * an array ({@code T[]}) or already with an ellipsis ({@code T...}), so the
     * marker is stripped before being re-applied — appending blindly produced
     * {@code T......}. The solver also spaces generic arguments
     * ({@code Function<A, B>}) where the declaration site does not, so the spaces
     * are removed.
     */
    private static String renderParameterType(ResolvedParameterDeclaration parameter) {
        String type = MethodSignatures.simpleTypeName(parameter.describeType());
        type = MethodSignatures.compactGenericArguments(type);
        if (!parameter.isVariadic()) {
            return type;
        }
        return stripVariadicMarker(type) + VARARGS;
    }

    /** Removes whichever variadic marker the solver used, so one can be applied cleanly. */
    private static String stripVariadicMarker(String type) {
        if (type.endsWith(VARARGS)) {
            return type.substring(0, type.length() - VARARGS.length());
        }
        if (type.endsWith(ARRAY_SUFFIX)) {
            return type.substring(0, type.length() - ARRAY_SUFFIX.length());
        }
        return type;
    }
}
