package io.github.hantsy.jdbc.sqlinit;

import io.github.hantsy.jdbc.sqlinit.resource.FileSystemResource;
import io.github.hantsy.jdbc.sqlinit.resource.Resource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationHistoryTest {

    @Test
    void createsTheTableAndTracksTheLifecycle() throws SQLException {
        DataSource dataSource = h2("history");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            MigrationHistory history = new MigrationHistory(connection, DbType.H2);

            history.ensureTable();
            assertTrue(history.applied().isEmpty());

            history.insertRunning(new Migration(1, "create", resource("V1__create.sql")));
            assertEquals(Map.of(1, "running"), history.applied());

            history.markSucceeded(1);
            assertEquals(Map.of(1, "succeeded"), history.applied());

            history.insertRunning(new Migration(2, "seed", resource("V2__seed.sql")));
            history.markFailed(2, "boom");
            assertEquals(Map.of(1, "succeeded", 2, "failed"), history.applied());
        }
    }

    private static Resource resource(String name) {
        return new FileSystemResource(Path.of(name));
    }

    private static DataSource h2(String database) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
