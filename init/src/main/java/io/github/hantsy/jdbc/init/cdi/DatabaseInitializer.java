package io.github.hantsy.jdbc.init.cdi;


import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Qualifier
@Retention(RUNTIME)
@Target({METHOD, FIELD, PARAMETER, TYPE})
public @interface DatabaseInitializer {

    public final static class Literal extends AnnotationLiteral<DatabaseInitializer> implements DatabaseInitializer {
        /**
         * Default DatabaseInitializer literal
         */
        public static final Literal INSTANCE = new Literal();
        private static final long serialVersionUID = 1L;

    }

}

