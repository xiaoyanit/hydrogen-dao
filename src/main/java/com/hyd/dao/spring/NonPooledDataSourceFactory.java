package com.hyd.dao.spring;

import com.hyd.dao.database.NonPooledDataSource;

/**
 * @author yidin
 */
public class NonPooledDataSourceFactory {

    static NonPooledDataSource createDataSource(DataSourceConfig props) {
        return new NonPooledDataSource(
                props.getDriverClassName(), props.getUrl(), props.getUsername(), props.getPassword());
    }
}
