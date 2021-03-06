package com.hyd.dao;

import com.hyd.dao.database.ExecutorFactory;
import com.hyd.dao.database.RowIterator;
import com.hyd.dao.database.TransactionManager;
import com.hyd.dao.database.commandbuilder.Command;
import com.hyd.dao.database.executor.Executor;
import com.hyd.dao.log.Logger;
import com.hyd.dao.snapshot.Snapshot;
import com.hyd.dao.util.BeanUtil;
import com.hyd.dao.util.Str;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/**
 * Main facade of hydrogen-dao.
 * <p/>
 * It must be created from {@link DataSources#getDAO(String)}
 * <p/>
 * It is thread safe.
 */
@SuppressWarnings("unchecked")
public class DAO {

    // initialization
    static {
        // HydrogenDAOInitializer.init();
    }

    public static final Date SYSDATE = new Date(0) {

        @Override
        public String toString() {
            return "SYSDATE";
        }
    };

    public static final int SYSDATE_TYPE = 33257679;

    private static final Logger LOG = Logger.getLogger(DAO.class);

    /////////////////////////////////////////////////////////

    /**
     * data source name
     */
    private String dsName;

    private ExecutorFactory executorFactory;

    /**
     * If it is out of current transaction
     */
    private boolean standAlone;

    protected DAO(String dsName) {
        this.dsName = dsName;
    }

    protected DAO(String dsName, boolean standAlone) {
        this.dsName = dsName;
        this.standAlone = standAlone;
    }

    protected DAO() {
    }

    /////////////////////////// TRANSACTION //////////////////////////////

    /**
     * Runs a transaction.
     *
     * @param runnable Transaction procedure.
     */
    public static void runTransaction(Runnable runnable) throws TransactionException {
        runTransaction(TransactionManager.DEFAULT_ISOLATION_LEVEL, runnable);
    }

    /**
     * Runs a transaction with specified isolation level.
     *
     * @param isolation One of the following values, default is
     *                  {@link TransactionManager#DEFAULT_ISOLATION_LEVEL}：
     *                  <ul>
     *                  <li>{@link java.sql.Connection#TRANSACTION_NONE}</li>
     *                  <li>{@link java.sql.Connection#TRANSACTION_READ_COMMITTED}</li>
     *                  <li>{@link java.sql.Connection#TRANSACTION_READ_UNCOMMITTED}</li>
     *                  <li>{@link java.sql.Connection#TRANSACTION_REPEATABLE_READ}</li>
     *                  <li>{@link java.sql.Connection#TRANSACTION_SERIALIZABLE}</li>
     *                  </ul>
     * @param runnable  Transaction procedure.
     */
    public static void runTransaction(int isolation, Runnable runnable) {
        TransactionManager.start();
        TransactionManager.setTransactionIsolation(isolation);

        try {
            runnable.run();
            TransactionManager.commit();
        } catch (TransactionException e) {
            TransactionManager.rollback();
            throw e;
        } catch (Exception e) {
            TransactionManager.rollback();
            throw new TransactionException(e);
        }
    }

    /////////////////// QUERY //////////////////////

    /**
     * 将 sql id 替换为真实的 SQL 语句，以及去掉语句结尾的分号
     *
     * @param sql 可能以分号结尾的 sql 语句或 sql id
     *
     * @return 修复后的 sql 语句
     */
    private static String fixSql(String sql) {
        return Str.removeEnd(sql.trim(), ";");
    }

    /**
     * 获得一个连接池快照。不同的数据源使用不同的连接池。
     *
     * @param dsName 数据源名称
     *
     * @return 该数据源的连接池快照
     */
    public static Snapshot getSnapshot(String dsName) {
        return Snapshot.getInstance(dsName);
    }

    /**
     * 某些情况下用户需要的是包含 Map 对象的 List
     *
     * @param rowList 包含 Row 对象的 List
     *
     * @return 包含 Map 对象的 List
     */
    public static List<Map<String, Object>> toMapList(List<Row> rowList) {
        return new ArrayList<>(rowList);
    }

    void setExecutorFactory(ExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    /**
     * 获得 dao 实例所属的数据源名称
     *
     * @return dao 实例所属的数据源名称
     */
    public String getDataSourceName() {
        return dsName;
    }

    ////////////////////////////////////////////////////////////////

    /**
     * 执行包装成 Command 对象的查询
     *
     * @param command 查询
     *
     * @return 查询结果
     */
    public List<Row> query(Command command) {
        return query(command.getStatement(), command.getParams());
    }

    /**
     * 执行包装成 Command 对象的查询，并将查询结果包装成指定的 Pojo 类对象
     *
     * @param clazz   要包装的类
     * @param command 查询
     *
     * @return 查询结果
     */
    public <T> List<T> query(Class<T> clazz, Command command) {
        return query(clazz, command.getStatement(), command.getParams());
    }

    public List<Row> query(MappedCommand mappedCommand) {
        return query(mappedCommand.toCommand());
    }

    public <T> List<T> query(Class<T> clazz, MappedCommand mappedCommand) {
        return query(clazz, mappedCommand.toCommand());
    }

    public List<Row> query(SQL.Generatable generatable) {
        return query(generatable.toCommand());
    }

    public <T> List<T> query(Class<T> clazz, SQL.Generatable generatable) {
        return query(clazz, generatable.toCommand());
    }

    /**
     * 执行带参数的查询
     *
     * @param sql    查询语句
     * @param params 参数
     *
     * @return 查询结果
     *
     * @throws DAOException 如果发生数据库错误
     */
    public List<Row> query(String sql, Object... params) throws DAOException {
        return query(null, sql, params);
    }

    /**
     * 执行查询，并以对象的方式返回查询结果。
     *
     * @param clazz  查询结果封装类
     * @param sql    查询语句
     * @param params 参数
     *
     * @return 查询结果
     *
     * @throws DAOException 如果发生数据库错误
     */
    public <T> List<T> query(Class<T> clazz, String sql, Object... params) throws DAOException {
        return queryRange(clazz, sql, -1, -1, params);
    }

    ////////////////////////////////////////////////////////////////

    public Row queryFirst(MappedCommand mappedCommand) {
        return queryFirst(mappedCommand.toCommand());
    }

    public <T> T queryFirst(Class<T> clazz, MappedCommand mappedCommand) {
        return queryFirst(clazz, mappedCommand.toCommand());
    }

    public Row queryFirst(Command command) {
        return queryFirst(command.getStatement(), command.getParams());
    }

    public <T> T queryFirst(Class<T> clazz, Command command) {
        return queryFirst(clazz, command.getStatement(), command.getParams());
    }

    /**
     * 返回第一个查询结果
     *
     * @param sql    查询语句
     * @param params 参数
     *
     * @return 查询结果
     *
     * @throws DAOException 如果发生数据库错误
     */
    public Row queryFirst(String sql, Object... params) throws DAOException {
        List<Row> list = query(sql, params);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    /**
     * 返回第一个查询结果
     *
     * @param sql    查询语句
     * @param clazz  包装类
     * @param params 参数
     *
     * @return 查询结果
     *
     * @throws DAOException 如果发生数据库错误
     */
    public <T> T queryFirst(Class<T> clazz, String sql, Object... params) throws DAOException {
        List<T> list = queryRange(clazz, sql, 0, 1, params);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public Row queryFirst(SQL.Generatable generatable) {
        Command command = generatable.toCommand();
        return queryFirst(command.getStatement(), command.getParams());
    }

    public <T> T queryFirst(Class<T> clazz, SQL.Generatable generatable) {
        Command command = generatable.toCommand();
        return queryFirst(clazz, command.getStatement(), command.getParams());
    }

    ////////////////////////////////////////////////////////////////

    public List<Row> queryRange(Command command, int startPosition, int endPosition) {
        return queryRange(command.getStatement(), startPosition, endPosition, command.getParams());
    }

    public <T> List<T> queryRange(Class<T> clazz, Command command, int startPosition, int endPosition) {
        return queryRange(clazz, command.getStatement(), startPosition, endPosition, command.getParams());
    }

    public List<Row> queryRange(SQL.Generatable generatable, int startPosition, int endPosition) {
        return queryRange(generatable.toCommand(), startPosition, endPosition);
    }

    public <T> List<T> queryRange(Class<T> clazz, SQL.Generatable generatable, int startPosition, int endPosition) {
        Command command = generatable.toCommand();
        return queryRange(clazz, command.getStatement(), startPosition, endPosition, command.getParams());
    }

    /**
     * 执行指定位置范围的带参数查询
     *
     * @param sql           查询语句
     * @param startPosition 获取查询结果的开始位置（包含）
     * @param endPosition   获取查询结果的结束位置（不包含）
     * @param params        参数
     *
     * @return 查询结果
     *
     * @throws DAOException         如果发生数据库错误
     * @throws NullPointerException 如果 sql 为 null
     */
    public List<Row> queryRange(String sql, int startPosition, int endPosition, Object... params) throws DAOException {
        return queryRange(null, sql, startPosition, endPosition, params);
    }

    /**
     * 执行指定位置范围的带参数查询
     *
     * @param clazz         查询结果包装类
     * @param sql           查询语句
     * @param startPosition 获取查询结果的开始位置（包含）
     * @param endPosition   获取查询结果的结束位置（不包含）
     * @param params        参数。如果是一个 List，则自动转换为 Array。
     *
     * @return 查询结果。如果 startPosition < 0 或 endPosition < 0 则表示返回所有的查询结果
     *
     * @throws DAOException 如果发生数据库错误
     */
    public <T> List<T> queryRange(
            Class<T> clazz, String sql, int startPosition, int endPosition, Object... params) throws DAOException {

        if (params.length == 1 && params[0] instanceof List) {
            List list = (List) params[0];
            return queryRange(clazz, sql, startPosition, endPosition, list.toArray(new Object[list.size()]));
        }

        String fixedSql = fixSql(sql);
        Executor executor = getExecutor();
        try {
            return executor.query(clazz, fixedSql, Arrays.asList(params), startPosition, endPosition);
        } finally {
            executor.finish();
        }
    }

    ////////////////////////////////////////////////////////////////

    public Page<Row> queryPage(SQL.Generatable generatable, int pageSize, int pageIndex) {
        Command command = generatable.toCommand();
        return queryPage(null, command.getStatement(), pageSize, pageIndex, command.getParams());
    }

    public <T> Page<T> queryPage(Class<T> clazz, SQL.Generatable generatable, int pageSize, int pageIndex) {
        Command command = generatable.toCommand();
        return queryPage(clazz, command.getStatement(), pageSize, pageIndex, command.getParams());
    }

    /**
     * 执行分页查询
     *
     * @param sql       查询命令
     * @param params    参数
     * @param pageSize  页大小
     * @param pageIndex 页号
     *
     * @return 查询结果
     *
     * @throws DAOException 如果发生数据库错误
     */
    public Page<Row> queryPage(
            String sql,
            int pageSize, int pageIndex, Object... params) throws DAOException {
        return queryPage(null, sql, pageSize, pageIndex, params);
    }

    /**
     * 执行分页查询
     *
     * @param wrappingClass 查询结果包装类
     * @param sql           查询命令
     * @param params        参数
     * @param pageSize      页大小
     * @param pageIndex     页号
     *
     * @return 查询结果
     *
     * @throws DAOException 如果发生数据库错误
     */
    public <T> Page<T> queryPage(
            Class<T> wrappingClass, String sql,
            int pageSize, int pageIndex, Object... params) throws DAOException {
        if (params.length == 1 && params[0] instanceof List) {
            List list = (List) params[0];
            return queryPage(wrappingClass, sql, pageSize, pageIndex, list.toArray(new Object[list.size()]));
        }

        String fixedSql = fixSql(sql);
        Executor executor = getExecutor();
        try {
            return executor.queryPage(wrappingClass, fixedSql, Arrays.asList(params), pageSize, pageIndex);
        } finally {
            executor.finish();
        }
    }

    ////////////////////////////////////////////////////////////////

    public RowIterator queryIterator(SQL.Generatable<SQL.Select> generatable) throws DAOException {
        return queryIterator(generatable.toCommand());
    }

    public RowIterator queryIterator(Command command) throws DAOException {
        return queryIterator(command.getStatement(), command.getParams());
    }

    public RowIterator queryIterator(SQL.Generatable<SQL.Select> generatable, Consumer<Row> preProcessor) throws DAOException {
        return queryIterator(generatable.toCommand(), preProcessor);
    }

    public RowIterator queryIterator(Command command, Consumer<Row> preProcessor) throws DAOException {
        return queryIterator(command.getStatement(), preProcessor, command.getParams());
    }

    public RowIterator queryIterator(String sql, Object... params) throws DAOException {
        return queryIterator(sql, null, params);
    }

    /**
     * 执行查询，返回迭代器
     * <p/>
     * <strong>注意：不关闭迭代器的话，可能造成数据库连接泄露！</strong>
     *
     * @param sql    要执行的查询语句
     * @param params 查询参数
     *
     * @return 用于获得查询结果的迭代器。如果查询语句为 null，则返回 null。
     *
     * @throws IllegalArgumentException 如果 sql 为 null
     * @throws DAOException             如果查询失败
     */
    public RowIterator queryIterator(String sql, Consumer<Row> preProcessor, Object... params) throws DAOException {

        if (sql == null) {
            throw new IllegalArgumentException("SQL is null");
        }

        if (params.length == 1 && params[0] instanceof List) {
            List list = (List) params[0];
            return queryIterator(sql, preProcessor, list.toArray(new Object[0]));
        }

        String fixedSql = fixSql(sql);
        Executor executor = getExecutor(true);
        return executor.queryIterator(fixedSql, Arrays.asList(params), preProcessor);
        // 数据库连接此时必须保持开启，所以不能调用 closeExecutor() 方法。
    }

    /**
     * 获取指定 sequence 的下一个值。注意，本方法仅用于 Oracle 数据库。
     *
     * @param sequenceName sequence 的名称
     *
     * @return 值
     *
     * @throws DAOException 如果发生数据库错误
     */
    public Long next(final String sequenceName) throws DAOException {
        Row row = queryFirst("select " + sequenceName + ".nextval val from dual");
        return row.getLongObject("val");
    }

    /**
     * 根据主键查找记录。前提是表的主键不是多个字段
     *
     * @param clazz     包装类
     * @param tableName 表名
     * @param key       主键值
     *
     * @return 找到的记录
     *
     * @throws DAOException 如果查询失败
     */
    public <T> T find(Class<T> clazz, String tableName, Object key) throws DAOException {
        Executor executor = getExecutor();
        try {
            return executor.find(clazz, key, tableName);
        } finally {
            executor.finish();
        }
    }

    /**
     * 根据主键查找记录。前提是表的主键不是多个字段
     *
     * @param clazz 包装类
     * @param key   主键值
     *
     * @return 找到的记录
     *
     * @throws DAOException 如果查询失败
     */
    public <T> T find(Class<T> clazz, Object key) throws DAOException {
        return find(clazz, BeanUtil.getTableName(clazz), key);
    }

    /**
     * 执行 select count 语句，并直接返回结果内容
     *
     * @param command 查询命令
     *
     * @return 结果中的数字
     */
    public int count(Command command) {
        Row row = queryFirst(command);
        Iterator<Object> iterator = row.values().iterator();
        return ((BigDecimal) iterator.next()).intValue();
    }

    /**
     * 执行 select count 语句，并直接返回结果内容
     *
     * @param sql    SQL 语句
     * @param params 参数
     *
     * @return 结果中的数字
     */
    public int count(String sql, Object... params) {
        Row row = queryFirst(sql, params);
        Iterator<Object> iterator = row.values().iterator();
        return ((BigDecimal) iterator.next()).intValue();
    }

    /**
     * 执行 select count 语句，并直接返回结果内容
     *
     * @param generatable 语句
     *
     * @return 结果中的数字
     */
    public int count(SQL.Generatable generatable) {
        Row row = queryFirst(generatable);
        Iterator<Object> iterator = row.values().iterator();
        return ((BigDecimal) iterator.next()).intValue();
    }

    /////////////////// UPDATE //////////////////////

    /**
     * 删除指定的一条记录
     *
     * @param obj       用于指定记录的对象，只要主键有值即可。
     * @param tableName 表名
     *
     * @return 受影响的行数
     *
     * @throws DAOException 如果执行数据库操作失败
     */
    public int delete(Object obj, String tableName) throws DAOException {
        Executor executor = getExecutor();
        try {
            return executor.delete(obj, tableName);
        } finally {
            executor.finish();
        }
    }

    public int deleteByKey(Object key, String tableName) {
        Executor executor = getExecutor();
        try {
            return executor.deleteByKey(key, tableName);
        } finally {
            executor.finish();
        }
    }

    /**
     * 执行 SQL 语句
     *
     * @param sql    要执行的语句
     * @param params 参数
     *
     * @return 受影响的行数
     *
     * @throws DAOException 如果发生数据库错误
     */
    public int execute(String sql, Object... params) throws DAOException {
        if (sql == null) {
            return 0;
        }
        String fixedSql = fixSql(sql);

        Executor executor = getExecutor();
        try {
            if (params.length == 1 && params[0] instanceof List) {
                List list = (List) params[0];
                return executor.execute(fixedSql, list);
            } else {
                return executor.execute(fixedSql, Arrays.asList(params));
            }
        } finally {
            executor.finish();
        }
    }

    /**
     * 执行批量语句
     *
     * @param command 批量语句
     *
     * @return 受影响的行数
     *
     * @throws DAOException 如果发生数据库错误
     */
    public int execute(BatchCommand command) throws DAOException {
        Executor executor = getExecutor();
        try {
            return executor.execute(command);
        } finally {
            executor.finish();
        }
    }

    public int execute(IteratorBatchCommand command) throws DAOException {
        Executor executor = getExecutor();
        try {
            return executor.execute(command);
        } finally {
            executor.finish();
        }
    }

    public int execute(SQL.Generatable generatable) {

        // 当 Generatable 发现无法生成可执行的 SQL 时，将返回 null
        Command command = generatable.toCommand();

        if (command != null) {
            return execute(command);
        } else {
            LOG.error("无法执行的语句：" + generatable.getClass());
            return 0;
        }
    }

    /**
     * 执行命令
     *
     * @param command 要执行的命令
     *
     * @return 受影响的行数
     *
     * @throws DAOException 如果发生数据库错误
     */
    public int execute(Command command) throws DAOException {
        return execute(command.getStatement(), command.getParams());
    }

    /**
     * 插入一条记录
     *
     * @param object    封装记录的对象
     * @param tableName 表名
     *
     * @throws DAOException 如果发生数据库错误
     */
    public void insert(Object object, String tableName) throws DAOException {
        if (object instanceof List) {
            insert((List) object, tableName);
            return;
        } else if (object instanceof Map) {
            insert((Map) object, tableName);
            return;
        }

        Executor executor = getExecutor();
        try {
            executor.insert(object, tableName);
        } finally {
            executor.finish();
        }
    }

    /**
     * 插入一条记录
     *
     * @param row       包含字段和值的记录
     * @param tableName 表名
     *
     * @throws DAOException 如果发生数据库错误
     */
    private void insert(Map row, String tableName) throws DAOException {
        Executor executor = getExecutor();
        try {
            executor.insertMap(row, tableName);
        } finally {
            executor.finish();
        }
    }

    /**
     * 插入一条记录
     *
     * @param obj 封装记录的对象
     *
     * @throws DAOException 如果发生数据库错误
     */
    public void insert(Object obj) throws DAOException {
        insert(obj, BeanUtil.getTableName(obj.getClass()));
    }

    /**
     * 批量插入记录
     *
     * @param objects   封装记录的对象
     * @param tableName 表名
     *
     * @throws DAOException 如果发生数据库错误
     */
    public void insert(List objects, String tableName) throws DAOException {
        if (objects == null || objects.isEmpty()) {
            return;
        }

        Executor executor = getExecutor();
        try {
            executor.insertList(objects, tableName);
        } finally {
            executor.finish();
        }
    }

    /**
     * 构造一个 Executor 对象。如果当前处于事务当中，则返回缓存于事务当中的 Executor 对象。
     *
     * @return Executor 对象
     *
     * @throws DAOException 如果发生数据库错误
     */
    private Executor getExecutor() throws DAOException {
        return getExecutor(this.standAlone);
    }

    /**
     * 构造一个 Executor 对象。如果 standalone 为 true，即使当前处于事务当中，这个
     * Executor 对象也会使用新的数据库连接，从而独立于事务执行数据库操作。
     *
     * @param standalone 是否脱离当前事务（如果有的话）
     *
     * @return Executor 对象
     *
     * @throws DAOException 如果发生数据库错误
     */
    private Executor getExecutor(boolean standalone) throws DAOException {
        return executorFactory.getExecutor(standalone);
    }

    /**
     * 调用存储过程
     *
     * @param name   存储过程名称
     * @param params 存储过程参数值
     *
     * @return 调用结果
     */
    public List call(String name, Object... params) {
        if (params.length == 1 && params[0] instanceof List) {
            List list = (List) params[0];
            call(name, list.toArray(new Object[list.size()]));
        }

        Executor executor = getExecutor();
        try {
            return executor.call(name, params);
        } finally {
            executor.finish();
        }
    }

    /**
     * 调用函数 (Oracle only)
     *
     * @param name   function 名称
     * @param params 调用参数
     *
     * @return 调用结果。第一个元素是 function 的返回值，第二个元素是第一个 OUT 或 IN_OUT 类型的参数，以此类推。
     *
     * @throws DAOException 如果调用失败
     */
    public List callFunction(String name, Object... params) throws DAOException {
        if (params.length == 1 && params[0] instanceof List) {
            List list = (List) params[0];
            callFunction(name, list.toArray(new Object[list.size()]));
        }

        Executor executor = getExecutor();
        try {
            return executor.callFunction(name, params);
        } finally {
            executor.finish();
        }
    }

    /**
     * 判断指定的记录是否存在
     *
     * @param obj       包含主键值的 Pojo 对象或 Map 对象
     * @param tableName 表名
     *
     * @return 如果记录存在则返回 true
     */
    public boolean exists(Object obj, String tableName) {
        Executor executor = getExecutor();
        try {
            return executor.exists(obj, tableName);
        } finally {
            executor.finish();
        }
    }

    /**
     * 判断本 DAO 对象是否是独立于当前事务之外
     *
     * @return 如果本 DAO 对象独立于事务之外，则返回 true
     */
    public boolean isStandAlone() {
        return this.standAlone;
    }

    void setStandAlone(boolean standAlone) {
        this.standAlone = standAlone;
    }
}
