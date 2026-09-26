package io.github.hantsy.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps one row of a JDBC {@link ResultSet} to an application object.
 *
 * <p>A row mapper contains only row-mapping logic. It does not advance the result
 * set, close JDBC resources, or handle the query lifecycle; {@link JdbcClient}
 * manages those responsibilities. Mappers are normally stateless and can be
 * safely reused for multiple queries.</p>
 *
 * <p>The row number is zero-based and identifies the row within the current
 * query result. For example, a lambda can map a result to a small projection:</p>
 *
 * <pre>{@code
 * RowMapper<String> names = (rs, rowNum) -> rs.getString("dev_name");
 *
 * List<String> values = client
 *         .sql("SELECT dev_name FROM engineers ORDER BY id")
 *         .query(names)
 *         .list();
 * }</pre>
 *
 * <p>For reusable mappings, implement this interface explicitly. The mapper
 * below maps column values to a domain object and may be shared by several
 * queries:</p>
 *
 * <pre>{@code
 * final class EngineerMapper implements RowMapper<Engineer> {
 *     @Override
 *     public Engineer mapRow(ResultSet rs, int rowNum) throws SQLException {
 *         return new Engineer(
 *                 rs.getLong("id"),
 *                 rs.getString("dev_name"));
 *     }
 * }
 *
 * List<Engineer> engineers = client
 *         .sql("SELECT id, dev_name FROM engineers ORDER BY id")
 *         .query(new EngineerMapper())
 *         .list();
 * }</pre>
 *
 * <p>Implementations should read the current row only and should avoid retaining
 * the {@code ResultSet} after this method returns. Any {@link SQLException}
 * raised while reading the row is propagated through the client's normal data
 * access exception handling.</p>
 *
 * @param <T> the type produced for each result-set row
 * @see JdbcClient.SqlSpec#query(RowMapper)
 */
@FunctionalInterface
public interface RowMapper<T> {
    /**
     * Maps the current result-set row.
     *
     * @param rs     the result set positioned at the row to map
     * @param rowNum the zero-based index of the row in the query result
     * @return the mapped object; normally non-null
     * @throws SQLException if a JDBC value cannot be read
     */
    T mapRow(ResultSet rs, int rowNum) throws SQLException;
}
