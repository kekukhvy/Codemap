package dev.codemap.core.entrypoint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import dev.codemap.core.model.DetectedBy;
import dev.codemap.core.model.EntryPoint;
import dev.codemap.core.model.EntryPointKind;
import dev.codemap.core.model.SourceLocation;
import dev.codemap.core.parse.ResolvedMethodIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Recognises programmatic route registration — {@code app.post(TASKS, taskHandler::create)}
 * — as a {@link EntryPointKind#REST} entry point (spec §4.1).
 *
 * <p>Kairos, the reference project, registers every REST route this way and uses
 * annotations only in its admin UI; an annotation-only detector would find
 * {@code main()} and the UI and miss the entire API. The matcher keys on three
 * things at the call site: the receiver is a known server type (Javalin, Spark),
 * the first argument resolves to a string, and the second is a method reference
 * or lambda naming the handler.
 */
final class ProgrammaticRouteEntryPointRule implements EntryPointRule {

    private static final Logger log = LoggerFactory.getLogger(ProgrammaticRouteEntryPointRule.class);
    private static final int UNKNOWN_LINE = 0;
    private static final int PATH_ARGUMENT_INDEX = 0;
    private static final int HANDLER_ARGUMENT_INDEX = 1;
    private static final int EXPECTED_ARGUMENT_COUNT = 2;
    private static final String ID_SEPARATOR = ":";

    private final ConstantFolder constantFolder = new ConstantFolder();

    @Override
    public List<EntryPoint> detect(CompilationUnit unit, String moduleId, String relativePath) {
        List<EntryPoint> entryPoints = new ArrayList<>();
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            toEntryPoint(call, moduleId, relativePath).ifPresent(entryPoints::add);
        }
        return entryPoints;
    }

    private Optional<EntryPoint> toEntryPoint(MethodCallExpr call, String moduleId, String relativePath) {
        HttpVerb verb = HttpVerb.fromRegistrationMethodName(call.getNameAsString());
        if (verb == null || !isRouteRegistration(call)) {
            return Optional.empty();
        }
        String methodId = handlerMethodId(call.getArgument(HANDLER_ARGUMENT_INDEX));
        if (methodId == null) {
            return Optional.empty();
        }
        String path = constantFolder.fold(call.getArgument(PATH_ARGUMENT_INDEX))
                .orElseGet(() -> constantFolder.textOf(call.getArgument(PATH_ARGUMENT_INDEX)));
        int line = call.getBegin().map(position -> position.line).orElse(UNKNOWN_LINE);
        return Optional.of(new EntryPoint(
                EntryPointIds.of(moduleId, EntryPointKind.REST, verb + ID_SEPARATOR + methodId),
                moduleId,
                EntryPointKind.REST,
                verb.labelFor(path),
                methodId,
                DetectedBy.RULE,
                new SourceLocation(relativePath, line)));
    }

    /**
     * Whether a call shape matches a route registration: two arguments, on a
     * receiver whose declared type is a known server type.
     */
    private boolean isRouteRegistration(MethodCallExpr call) {
        return call.getArguments().size() == EXPECTED_ARGUMENT_COUNT
                && call.getScope().isPresent()
                && isKnownServerReceiver(call.getScope().get());
    }

    private boolean isKnownServerReceiver(Expression scope) {
        if (!scope.isNameExpr()) {
            return false;
        }
        return declaredTypeName(scope.asNameExpr())
                .map(ServerTypes::isKnownServerType)
                .orElse(false);
    }

    /**
     * The simple type name a variable was declared with, read from the AST rather
     * than the symbol solver — the analysed project's server library may not be on
     * the resolvable classpath, and the declared name alone is already unambiguous.
     */
    @SuppressWarnings("unchecked") // JavaParser's varargs findAncestor(Class<N>...) triggers this unavoidably.
    private Optional<String> declaredTypeName(NameExpr reference) {
        String name = reference.getNameAsString();
        return reference.findAncestor(CallableDeclaration.class)
                .flatMap(declaration -> parameterType(declaration, name))
                .or(() -> variableType(reference, name))
                .map(SimpleNames::of);
    }

    private Optional<String> parameterType(CallableDeclaration<?> declaration, String name) {
        return declaration.getParameters().stream()
                .filter(parameter -> parameter.getNameAsString().equals(name))
                .map(Parameter::getType)
                .map(Node::toString)
                .findFirst();
    }

    @SuppressWarnings("unchecked") // JavaParser's varargs findAncestor(Class<N>...) triggers this unavoidably.
    private Optional<String> variableType(NameExpr reference, String name) {
        return reference.findAncestor(TypeDeclaration.class)
                .stream()
                .flatMap(type -> type.findAll(VariableDeclarationExpr.class).stream())
                .flatMap(declarationExpr -> declarationExpr.getVariables().stream())
                .filter(variable -> variable.getNameAsString().equals(name))
                .map(variable -> variable.getType().toString())
                .findFirst();
    }


    /**
     * The id of the handler method a route was registered with.
     *
     * <p>Only a method reference ({@code taskHandler::create}) names a handler
     * directly enough to build a method id; a lambda body may call several
     * methods, or none, so it is not treated as an entry point target.
     *
     * <p>Resolving via {@link MethodReferenceExpr#resolve()} would require
     * resolving the server library's own overload of {@code post(...)} to know
     * which functional interface the reference is being coerced to — exactly the
     * dependency a project may not have on its resolvable classpath. Resolving the
     * reference's own scope type is independent of that and enough to locate the
     * method by name, so it is preferred here.
     */
    private String handlerMethodId(Expression handlerArgument) {
        if (!(handlerArgument instanceof MethodReferenceExpr reference)) {
            return null;
        }
        try {
            ResolvedType scopeType = reference.getScope().calculateResolvedType();
            return scopeType.isReferenceType()
                    ? matchingMethod(scopeType.asReferenceType(), reference.getIdentifier())
                    : null;
        } catch (RuntimeException e) {
            log.debug("Could not resolve route handler {}: {}", reference, e.getMessage());
            return null;
        }
    }

    /**
     * The id of the first declared method matching a reference's name.
     *
     * <p>Route handlers are conventionally not overloaded, so matching by name
     * alone is enough; picking the first match on the rare overloaded handler is
     * an accepted degradation rather than a reason to drop the entry point.
     */
    private String matchingMethod(ResolvedReferenceType scopeType, String methodName) {
        return scopeType.getTypeDeclaration()
                .flatMap(declaration -> declaration.getDeclaredMethods().stream()
                        .filter(method -> method.getName().equals(methodName))
                        .findFirst())
                .map(ResolvedMethodIds::idOf)
                .orElse(null);
    }
}
