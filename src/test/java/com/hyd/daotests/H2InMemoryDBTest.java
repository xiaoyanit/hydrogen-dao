package com.hyd.daotests;

import com.hyd.dao.DAO;
import com.hyd.dao.DataSources;
import com.hyd.dao.Row;
import com.hyd.dao.database.ColumnInfo;
import com.hyd.dao.database.commandbuilder.helper.CommandBuilderHelper;
import com.hyd.dao.util.DBCPDataSource;
import com.hyd.dao.util.ResultSetUtil;
import java.sql.SQLException;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.Test;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;

/**
 * @author yiding_he
 */
public class H2InMemoryDBTest {

    @Test
    public void testCreateImMemoryDB() throws Exception {
        BasicDataSource dataSource = DBCPDataSource.newH2MemDataSource();
        DataSources dataSources = new DataSources();
        dataSources.setDataSource("h2", dataSource);

        DAO dao = dataSources.getDAO("h2");
        List<Row> schemas = dao.query("show schemas");
        schemas.forEach(System.out::println);

        dao.execute("create table table1(id int primary key, name varchar(100))");
        dao.execute("insert into table1 set id=?, name=?", 0, "Hello, world");
        dao.query("select * from table1").forEach(System.out::println);

        dataSources.withConnection("h2", connection -> {
            try {
                ResultSet columns = connection.getMetaData().getColumns(null, "PUBLIC", "TABLE1", "%");
                HashMap[] maps = ResultSetUtil.readResultSet(columns);
                if (maps.length > 0) {
                    for (HashMap map : maps) {
                        System.out.println(map);
                    }
                } else {
                    System.err.println("No column found for table1");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        dataSources.withConnection("h2", connection -> {
            try {
                CommandBuilderHelper helper = CommandBuilderHelper.getHelper(connection);
                ColumnInfo[] columnInfos = helper.getColumnInfos("PUBLIC", "table1");
                for (ColumnInfo columnInfo : columnInfos) {
                    System.out.println(columnInfo);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}
