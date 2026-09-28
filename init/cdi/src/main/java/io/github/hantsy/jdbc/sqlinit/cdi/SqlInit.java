package io.github.hantsy.jdbc.sqlinit.cdi;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Qualifies the {@link javax.sql.DataSource} that SQL script initialization runs against.
 *
 * <p>An application that declares several data sources uses this to pick the one that should be
 * initialized. When no {@code @SqlInit} qualified data source exists, the default unqualified
 * {@code DataSource} bean is used instead.</p>
 */
@Qualifier
@Retention(RUNTIME)
@Target({METHOD, FIELD, PARAMETER, TYPE})
public @interface SqlInit {

    /**
     * Supports programmatic lookup of the {@link SqlInit} qualifier.
     */
    final class Literal extends AnnotationLiteral<SqlInit> implements SqlInit {

        /**
         * Default SqlInit literal
         */
        public static final Literal INSTANCE = new Literal();
        private static final long serialVersionUID = 1L;
    }
}
