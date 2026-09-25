package io.github.hantsy.jdbc.tx.it.service;

import io.github.hantsy.jdbc.tx.support.TransactionSynchronizationManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Inner bean whose methods report whether a transaction is active inside them, so the propagation
 * tests can observe join/suspend behavior. Lives in a separate bean because CDI interceptors do not
 * apply to self-invocation.
 */
@ApplicationScoped
public class InnerService {

    @Transactional(Transactional.TxType.REQUIRED)
    public boolean required() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public boolean requiresNew() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public boolean supports() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public boolean notSupported() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void mandatory() {
    }

    @Transactional(Transactional.TxType.NEVER)
    public void never() {
    }
}
