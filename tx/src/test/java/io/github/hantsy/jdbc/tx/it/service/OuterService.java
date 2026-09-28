package io.github.hantsy.jdbc.tx.it.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Outer bean that runs {@code REQUIRED} and delegates to {@link InnerService}, so the propagation
 * tests can observe the inner transaction state through CDI proxies.
 */
@ApplicationScoped
public class OuterService {

    @Inject
    private InnerService inner;

    @Transactional(Transactional.TxType.REQUIRED)
    public boolean callRequired() {
        return inner.required();
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public boolean callRequiresNew() {
        return inner.requiresNew();
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public boolean callSupports() {
        return inner.supports();
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public boolean callNotSupported() {
        return inner.notSupported();
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public void callMandatory() {
        inner.mandatory();
    }

    @Transactional(Transactional.TxType.REQUIRED)
    public void callNever() {
        inner.never();
    }
}
