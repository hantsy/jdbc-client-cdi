package io.github.hantsy.jdbc.tx.cdi;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Internal interceptor binding. The {@code jakarta.transaction.Transactional} {@code value()}
 * attribute is a CDI binding attribute, so a single interceptor declared with
 * {@code @Transactional} only matches {@code TxType.REQUIRED}. This marker binding has no binding
 * attributes; {@link TransactionalCdiExtension} adds it to every {@code @Transactional} type, and
 * {@link TransactionalInterceptor} reads the real annotation itself.
 */
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TransactionalBinding {

    final class Literal extends AnnotationLiteral<TransactionalBinding> implements TransactionalBinding {
        public static final Literal INSTANCE = new Literal();

        private static final long serialVersionUID = 1L;
    }
}
