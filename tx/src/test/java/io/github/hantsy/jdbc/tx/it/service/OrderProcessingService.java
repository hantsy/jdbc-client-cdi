package io.github.hantsy.jdbc.tx.it.service;

import io.github.hantsy.jdbc.tx.support.TransactionContextHolder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.Executors;

/**
 * Fixture bean exercising the classification and concurrency scenarios.
 */
@ApplicationScoped
public class OrderProcessingService {

    @Inject
    private DataSource dataSource;

    @Inject
    private IsolatedChildService isolatedChildService;

    /** Scenario A: transaction context is visible inside virtual-thread subtasks. */
    @Transactional(Transactional.TxType.REQUIRED)
    public boolean processParallelInTransaction() throws Exception {
        if (!TransactionContextHolder.isBound()) {
            return false;
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var taskA = executor.submit(() -> {
                try (Connection conn = dataSource.getConnection()) {
                    return TransactionContextHolder.isBound();
                }
            });
            var taskB = executor.submit(() -> {
                try (Connection conn = dataSource.getConnection()) {
                    return TransactionContextHolder.isBound();
                }
            });
            return taskA.get() && taskB.get();
        }
    }

    /** Scenario B: explicit rollback on a checked exception. */
    @Transactional(rollbackOn = {CustomCheckedException.class})
    public void writeAndThrowCustomChecked() throws Exception {
        insert(2, "Rollback Me");
        throw new CustomCheckedException();
    }

    /** Scenario C: explicit no-rollback on a runtime exception. */
    @Transactional(dontRollbackOn = {CustomUncheckedException.class})
    public void writeAndThrowCustomUnchecked() throws Exception {
        insert(3, "Commit Me");
        throw new CustomUncheckedException();
    }

    /** Scenario D: parent REQUIRED with an isolated REQUIRES_NEW child. */
    @Transactional(Transactional.TxType.REQUIRED)
    public void executeParentWithIsolatedChild() throws Exception {
        insert(10, "Parent Data");
        try {
            isolatedChildService.executeIsolatedChildTransaction();
        } catch (RuntimeException e) {
            // suppress child failure to verify isolation
        }
    }

    /** Default rule: a RuntimeException rolls back. */
    @Transactional
    public void writeAndThrowRuntime() throws Exception {
        insert(4, "Runtime Rollback");
        throw new IllegalStateException("runtime");
    }

    /** Default rule: a checked exception commits. */
    @Transactional
    public void writeAndThrowChecked() throws Exception {
        insert(5, "Checked Commit");
        throw new Exception("checked");
    }

    /** Default rule: an Error rolls back (rollback on RuntimeException or Error). */
    @Transactional
    public void writeAndThrowError() throws Exception {
        insert(6, "Error Rollback");
        throw new AssertionError("error");
    }

    /** dontRollbackOn takes precedence over rollbackOn. */
    @Transactional(rollbackOn = {CustomUncheckedException.class}, dontRollbackOn = {CustomUncheckedException.class})
    public void writeAndThrowInBoth() throws Exception {
        insert(7, "Both Commit");
        throw new CustomUncheckedException();
    }

    private void insert(int id, String detail) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO orders (id, detail) VALUES (?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, detail);
            ps.executeUpdate();
        }
    }
}
