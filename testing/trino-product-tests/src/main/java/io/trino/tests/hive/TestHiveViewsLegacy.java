/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.tests.hive;

import io.trino.tempto.BeforeTestWithContext;
import io.trino.tempto.Requires;
import io.trino.tempto.fulfillment.table.hive.tpch.ImmutableTpchTablesRequirements.ImmutableNationTable;
import io.trino.tempto.fulfillment.table.hive.tpch.ImmutableTpchTablesRequirements.ImmutableOrdersTable;
import io.trino.tempto.query.QueryExecutor;
import org.testng.annotations.Test;

import java.math.BigDecimal;

import static io.trino.tempto.assertions.QueryAssert.Row.row;
import static io.trino.tempto.assertions.QueryAssert.assertThat;
import static io.trino.tempto.query.QueryExecutor.query;
import static io.trino.tests.TestGroups.HIVE_VIEWS;
import static io.trino.tests.utils.QueryExecutors.onHive;
import static io.trino.tests.utils.QueryExecutors.onPresto;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Requires({
        ImmutableNationTable.class,
        ImmutableOrdersTable.class,
})
public class TestHiveViewsLegacy
        extends AbstractTestHiveViews
{
    @BeforeTestWithContext
    public void setup()
    {
        setSessionProperty("hive.legacy_hive_view_translation", "true");
    }

    @Override
    @Test(groups = HIVE_VIEWS)
    public void testShowCreateView()
    {
        onHive().executeQuery("DROP VIEW IF EXISTS hive_show_view");

        onHive().executeQuery("CREATE VIEW hive_show_view AS SELECT * FROM nation");

        // view SQL depends on Hive distribution
        assertThat(query("SHOW CREATE VIEW hive_show_view")).hasRowsCount(1);
    }

    @Test(groups = HIVE_VIEWS)
    public void testUnsupportedLateralViews()
    {
        onHive().executeQuery("DROP VIEW IF EXISTS hive_lateral_view");
        onHive().executeQuery("DROP TABLE IF EXISTS pageAds");

        onHive().executeQuery("CREATE TABLE pageAds(pageid string, adid_list array<int>)");
        onHive().executeQuery("CREATE VIEW hive_lateral_view as SELECT pageid, adid FROM pageAds LATERAL VIEW explode(adid_list) adTable AS adid");

        assertThat(() -> query("SELECT COUNT(*) FROM hive_lateral_view"))
                .failsWithMessage("Failed parsing stored view 'hive.default.hive_lateral_view': line 1:78: mismatched input 'VIEW'");
    }

    @Override
    @Test(groups = HIVE_VIEWS)
    public void testHiveViewInInformationSchema()
    {
        onHive().executeQuery("DROP SCHEMA IF EXISTS test_schema CASCADE;");

        onHive().executeQuery("CREATE SCHEMA test_schema;");
        onHive().executeQuery("CREATE VIEW test_schema.hive_test_view AS SELECT * FROM nation");
        onHive().executeQuery("CREATE TABLE test_schema.hive_table(a string)");
        onPresto().executeQuery("CREATE TABLE test_schema.trino_table(a int)");
        onPresto().executeQuery("CREATE VIEW test_schema.trino_test_view AS SELECT * FROM nation");

        boolean hiveWithTableNamesByType = getHiveVersionMajor() >= 3 ||
                (getHiveVersionMajor() == 2 && getHiveVersionMinor() >= 3);
        assertThat(query("SELECT * FROM information_schema.tables WHERE table_schema = 'test_schema'")).containsOnly(
                row("hive", "test_schema", "trino_table", "BASE TABLE"),
                row("hive", "test_schema", "hive_table", "BASE TABLE"),
                row("hive", "test_schema", "hive_test_view", hiveWithTableNamesByType ? "VIEW" : "BASE TABLE"),
                row("hive", "test_schema", "trino_test_view", "VIEW"));

        assertThat(query("SELECT view_definition FROM information_schema.views WHERE table_schema = 'test_schema' and table_name = 'hive_test_view'")).containsOnly(
                row("SELECT \"nation\".\"n_nationkey\", \"nation\".\"n_name\", \"nation\".\"n_regionkey\", \"nation\".\"n_comment\" FROM \"default\".\"nation\""));

        assertThat(query("DESCRIBE test_schema.hive_test_view"))
                .contains(row("n_nationkey", "bigint", "", ""));
    }

    @Override
    @Test(groups = HIVE_VIEWS)
    public void testHiveViewWithParametrizedTypes()
    {
        onHive().executeQuery("DROP VIEW IF EXISTS hive_view_parametrized");
        onHive().executeQuery("DROP TABLE IF EXISTS hive_table_parametrized");

        onHive().executeQuery("CREATE TABLE hive_table_parametrized(a decimal(20,4), b bigint, c varchar(20))");
        onHive().executeQuery("CREATE VIEW hive_view_parametrized AS SELECT * FROM hive_table_parametrized");
        onHive().executeQuery("INSERT INTO TABLE hive_table_parametrized VALUES (1.2345, 42, 'bar')");

        assertThat(query("SELECT * FROM hive.default.hive_view_parametrized")).containsOnly(
                row(new BigDecimal("1.2345"), 42, "bar"));

        assertThat(query("SELECT data_type FROM information_schema.columns WHERE table_name = 'hive_view_parametrized'")).containsOnly(
                row("decimal(20,4)"),
                row("bigint"),
                row("varchar(20)"));
    }

    @Override
    @Test
    public void testLateralViewExplode()
    {
        assertThatThrownBy(super::testLateralViewExplode)
                .hasMessageContaining("Failed parsing stored view 'hive.default.hive_lateral_view': line 1:78: mismatched input 'VIEW'");
    }

    @Override
    public void testNestedHiveViews()
    {
        assertThatThrownBy(super::testNestedHiveViews)
                .hasMessageContaining("View 'hive.default.nested_top_view' is stale or in invalid state: column [n_renamed] of type varchar projected from query view at position 0 cannot be coerced to column [n_renamed] of type varchar(25) stored in view definition");
    }

    @Override
    protected QueryExecutor connectToPresto(String catalog)
    {
        QueryExecutor executor = super.connectToPresto(catalog);
        executor.executeQuery("SET SESSION hive.legacy_hive_view_translation = true");
        return executor;
    }
}
