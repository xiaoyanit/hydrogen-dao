package com.hyd.dao.sp;

import com.hyd.dao.DAOException;
import com.hyd.dao.util.BeanUtil;
import com.hyd.dao.util.ResultSetUtil;
import com.hyd.dao.util.Str;

import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 执行存储过程帮助类
 */
public class StorageProsedureHelper {

    public static final Map<Integer, SpParamType> sp_param_types = new HashMap<Integer, SpParamType>() {

        {
            put(1, SpParamType.IN);
            put(2, SpParamType.IN_OUT);
            put(4, SpParamType.OUT);
        }
    };

    /**
     * 创建一个 CallableStatement
     *
     * @param name       存储过程名
     * @param params     调用参数
     * @param connection 数据库连接
     *
     * @return CallableStatement 对象
     *
     * @throws SQLException 如果创建失败
     */
    public static CallableStatement createCallableStatement(
            String name, SpParam[] params, Connection connection) throws SQLException {
        String call_str = generateCallStatement(name, params);
        CallableStatement cs = connection.prepareCall(call_str);
        setupParams(params, cs);
        return cs;
    }

    /**
     * 设置存储过程的调用参数，以及注册返回值
     *
     * @param params 参数
     * @param cs     要执行的 CallableStatement
     *
     * @throws SQLException 如果设置参数失败
     */
    private static void setupParams(SpParam[] params, CallableStatement cs) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            SpParam param = params[i];
            if ((param.getType() == SpParamType.IN || param.getType() == SpParamType.IN_OUT)
                    && param.getValue() != null) {
                cs.setObject(i + 1, param.getValue());
            }
            if (param.getType() == SpParamType.OUT || param.getType() == SpParamType.IN_OUT) {
                if (Str.isEmptyString(param.getName())) {
                    cs.registerOutParameter(i + 1, param.getSqlType());
                } else {
                    cs.registerOutParameter(i + 1, param.getSqlType(), param.getName());
                }
            }
        }
    }

    /**
     * 生成一个调用存储过程的语句，格式类似于“{call XXX(?,?,?)}”
     *
     * @param name   存储过程名
     * @param params 参数
     *
     * @return 调用存储过程的语句
     */
    private static String generateCallStatement(String name, SpParam[] params) {
        String call_str = "{call " + name + "(";
        for (int i = 0; i < params.length; i++) {
            call_str += "?" + (i == params.length - 1 ? "" : ",");
        }
        call_str += ")}";
        return call_str;
    }

    /**
     * 创建存储过程调用参数
     *
     * @param name   存储过程名称
     * @param params 参数值
     * @param conn   数据库连接（执行完后不会关闭）
     *
     * @return 存储过程调用参数
     *
     * @throws SQLException 如果获取存储过程信息失败
     */
    public static SpParam[] createSpParams(String name, Object[] params, Connection conn) throws SQLException {
        try {
            HashMap[] rows = getSpParamDefinitions(conn, name);

            SpParam[] sp_params = new SpParam[rows.length];
            int param_counter = 0;
            for (int i = 0; i < rows.length; i++) {
                HashMap row = rows[i];

                int data_type = getIntegerValue(row, "data_type");
                int column_type = getIntegerValue(row, "column_type");

                SpParamType type = sp_param_types.get(column_type);
                Object value;
                if (type != SpParamType.OUT) {
                    value = params[param_counter];
                    param_counter++;
                } else {
                    value = null;
                }
                sp_params[i] = new SpParam(type, data_type, value);
            }

            return sp_params;
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new DAOException(e);
        }
    }

    private static int getIntegerValue(HashMap row, String colName) {
        Object dataType = row.get(colName);

        if (dataType != null) {
            return new BigDecimal(dataType.toString()).intValue();
        } else {
            throw new DAOException("Unknown procedure parameter type: " + row);
        }
    }

    /**
     * 查询存储过程参数信息
     *
     * @param conn   数据库连接
     * @param spName 存储过程名称
     *
     * @return 参数信息
     *
     * @throws Exception 如果获取存储过程信息失败
     */
    private static HashMap[] getSpParamDefinitions(Connection conn, String spName) throws Exception {
        DatabaseMetaData metaData = conn.getMetaData();

        String schema;

        if (spName.indexOf(".") == -1) {
            schema = metaData.getUserName().toUpperCase();
        } else {
            schema = spName.split("\\.")[0].toUpperCase();
            spName = spName.substring(spName.lastIndexOf(".") + 1);
        }

        ResultSet procedures = metaData.getProcedureColumns(null, schema, spName.toUpperCase(), "%");

        HashMap[] rows = ResultSetUtil.readResultSet(procedures);
        BeanUtil.sort(rows, "sequence");    // 按参数的顺序排列
        return rows;
    }
}
