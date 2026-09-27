# Updates, generated keys, and batches

`JdbcClient` supports write operations, generated keys, and efficient batch updates without leaving the fluent API. The
examples below cover the common patterns used when modifying data in an application.

## Updating data

Use `update()` for SQL statements such as `INSERT`, `UPDATE`, and `DELETE`. The method returns the number of rows
affected.

```java
int rows = client
        .sql("UPDATE engineers SET dev_name = :name WHERE id = :id")
        .param("name", "Ada")
        .param("id", 1L)
        .update();
```

## Handling generated keys

When inserting a row and generated keys are required, pass a `GeneratedKeyHolder` to the update call.

```java
GeneratedKeyHolder keys = new GeneratedKeyHolder();

int rows = client
        .sql("INSERT INTO engineers (dev_name) VALUES (:name)")
        .param("name", "Ada")
        .update(keys);

Object generatedKey = keys.getKey();
Map<String, Object> firstRow = keys.getKeys();
List<Map<String, Object>> allRows = keys.getKeyList();
```

The JDBC driver must support generated keys. `getKey()` returns the first column from the first generated-key row, while
`getKeys()` and `getKeyList()` preserve column labels and multiple rows.

## Executing batch updates

Use a list of named-argument maps for batch updates:

```java
int[] results = client
        .sql("UPDATE engineers SET active = :active WHERE id = :id")
        .batchUpdate(List.of(
                Map.of("active", true, "id", 1L),
                Map.of("active", false, "id", 2L)
        ));
```

For positional SQL, pass an array for each batch entry:

```java
int[] results = client
        .sql("UPDATE engineers SET active = ? WHERE id = ?")
        .batchUpdate(new Object[][]{
                {true, 1L},
                {false, 2L}
        });
```

These patterns keep write operations compact and consistent while exposing the database-specific capabilities needed for
generated keys and bulk updates.

For query execution, parameter binding, and result mapping, see [querying and result mapping](querying.md).
