package dev.codemap.core.parse;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.Optional;

/**
 * Identifies the method id and class id of a {@link CallableDeclaration}, for
 * callers outside this package that need to address a declaration the same way
 * {@link JavaSourceParser} indexed it.
 *
 * <p>The call graph is built as a second pass over already-parsed files (see
 * {@code dev.codemap.core.graph.CallGraphBuilder}), so it needs to derive the
 * "from" side of an edge from a plain AST declaration without repeating
 * {@link MethodSignatures}' id logic.
 */
public final class MethodOwner {

    private static final String NAME_SEPARATOR = ".";
    private static final String DEFAULT_PACKAGE = "";

    private static final MethodSignatures SIGNATURES = new MethodSignatures();

    private MethodOwner() {
    }

    /**
     * The id of a callable declaration, in the same format {@code JavaSourceParser}
     * assigns {@link dev.codemap.core.model.IndexedMethod#id()}.
     *
     * @param callable a method or constructor declaration
     * @return the id, or {@code null} when the declaration has no enclosing named
     *         type (for example a callable declared inside an anonymous class)
     */
    public static String idOf(CallableDeclaration<?> callable) {
        String classId = classIdOf(callable);
        return classId == null ? null : SIGNATURES.methodId(classId, callable);
    }

    /**
     * The id of the type that declares a callable, in the dotted nested-type form
     * {@code JavaSourceParser} uses.
     *
     * @param callable a method or constructor declaration
     * @return the owning type's id, or {@code null} when it has no name to build
     *         one from
     */
    public static String classIdOf(CallableDeclaration<?> callable) {
        return enclosingTypeName(callable).orElse(null);
    }

    private static Optional<String> enclosingTypeName(Node node) {
        Optional<Node> parent = node.getParentNode();
        while (parent.isPresent()) {
            if (parent.get() instanceof TypeDeclaration<?> type) {
                return nestedName(type);
            }
            parent = parent.get().getParentNode();
        }
        return Optional.empty();
    }

    /** Builds the dotted fully-qualified name of a (possibly nested) type declaration. */
    private static Optional<String> nestedName(TypeDeclaration<?> type) {
        StringBuilder name = new StringBuilder(type.getNameAsString());
        Optional<Node> parent = type.getParentNode();
        while (parent.isPresent()) {
            if (parent.get() instanceof TypeDeclaration<?> outer) {
                name.insert(0, outer.getNameAsString() + NAME_SEPARATOR);
                parent = outer.getParentNode();
            } else {
                break;
            }
        }
        String packageName = type.findCompilationUnit()
                .flatMap(unit -> unit.getPackageDeclaration())
                .map(declaration -> declaration.getNameAsString())
                .orElse(DEFAULT_PACKAGE);
        return Optional.of(packageName.isEmpty() ? name.toString() : packageName + NAME_SEPARATOR + name);
    }
}
