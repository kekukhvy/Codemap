package dev.codemap.core.parse;

import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import dev.codemap.core.model.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a method or constructor's {@link Visibility} from its declaration.
 *
 * <p>An interface method with no explicit modifier is implicitly public — the
 * language does not require {@code public} to be written, but the member is
 * exported all the same (spec 007 §5.1). Every other declaration without an
 * explicit modifier is package-private, the JLS default.
 *
 * <p>A modifier set that cannot be read degrades to {@link Visibility#PACKAGE}
 * rather than throwing, so one odd declaration never fails the whole run.
 */
final class MethodVisibilities {

    private static final Logger log = LoggerFactory.getLogger(MethodVisibilities.class);

    private MethodVisibilities() {
    }

    /**
     * Determines the visibility of one callable declaration.
     *
     * @param callable the method or constructor
     * @param declaringType the type it is declared on, to detect interface membership
     * @return the declaration's visibility, defaulting to {@link Visibility#PACKAGE}
     */
    static Visibility of(CallableDeclaration<?> callable, TypeDeclaration<?> declaringType) {
        try {
            Visibility explicit = fromAccessSpecifier(callable.getAccessSpecifier());
            if (explicit != null) {
                return explicit;
            }
            return isInterfaceMember(declaringType) ? Visibility.PUBLIC : Visibility.PACKAGE;
        } catch (RuntimeException e) {
            log.debug("Could not read visibility of {}: {}", callable.getNameAsString(), e.getMessage());
            return Visibility.PACKAGE;
        }
    }

    private static Visibility fromAccessSpecifier(AccessSpecifier accessSpecifier) {
        return switch (accessSpecifier) {
            case PUBLIC -> Visibility.PUBLIC;
            case PROTECTED -> Visibility.PROTECTED;
            case PRIVATE -> Visibility.PRIVATE;
            case NONE -> null;
        };
    }

    private static boolean isInterfaceMember(TypeDeclaration<?> declaringType) {
        return declaringType.isClassOrInterfaceDeclaration()
                && declaringType.asClassOrInterfaceDeclaration().isInterface();
    }
}
