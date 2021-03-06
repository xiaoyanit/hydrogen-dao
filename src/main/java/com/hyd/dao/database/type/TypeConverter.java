package com.hyd.dao.database.type;

import com.hyd.dao.log.Logger;
import com.hyd.dao.util.BeanUtil;
import com.hyd.dao.util.Str;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * 将查询结果封装为 pojo 对象的类
 */
@SuppressWarnings({"unchecked"})
public class TypeConverter {

    static final Logger LOG = Logger.getLogger(TypeConverter.class);

    private static Map<String, String> convertBuffer = new HashMap<String, String>();

    private static ThreadLocal<List<String>> warnedMsgs = new ThreadLocal<List<String>>();

    static Map<Class, Class> primitiveToWrapper = new HashMap<Class, Class>();

    static Map<Class, Class> wrapperToPrimitive = new HashMap<Class, Class>();

    static {
        primitiveToWrapper.put(Boolean.TYPE, Boolean.class);
        primitiveToWrapper.put(Byte.TYPE, Byte.class);
        primitiveToWrapper.put(Short.TYPE, Short.class);
        primitiveToWrapper.put(Character.TYPE, Character.class);
        primitiveToWrapper.put(Integer.TYPE, Integer.class);
        primitiveToWrapper.put(Long.TYPE, Long.class);
        primitiveToWrapper.put(Float.TYPE, Float.class);
        primitiveToWrapper.put(Double.TYPE, Double.class);
        wrapperToPrimitive.put(Boolean.class, Boolean.TYPE);
        wrapperToPrimitive.put(Byte.class, Byte.TYPE);
        wrapperToPrimitive.put(Short.class, Short.TYPE);
        wrapperToPrimitive.put(Character.class, Character.TYPE);
        wrapperToPrimitive.put(Integer.class, Integer.TYPE);
        wrapperToPrimitive.put(Long.class, Long.TYPE);
        wrapperToPrimitive.put(Float.class, Float.TYPE);
        wrapperToPrimitive.put(Double.class, Double.TYPE);
    }

    private TypeConverter() {

    }

    public static Class getPrimitive(Class wrapperClass) {
        return wrapperToPrimitive.get(wrapperClass);
    }

    public static Class getWrapper(Class primitiveClass) {
        return primitiveToWrapper.get(primitiveClass);
    }

    /**
     * 将查询结果封装为指定对象
     *
     * @param clazz        封装结果的类
     * @param simpleResult 查询结果
     *
     * @return 封装后的对象集合
     *
     * @throws Exception 如果封装失败
     */
    @SuppressWarnings({"unchecked"})
    public static List<Object> convert(Class clazz, List<Object> simpleResult) throws Exception { // NOSONAR
        ArrayList<Object> result = new ArrayList<Object>();

        for (Object obj : simpleResult) {
            Map<String, Object> row = (Map<String, Object>) obj;
            Object converted = convertRow(clazz, row);
            result.add(converted);
        }

        warnedMsgs.set(new ArrayList<String>());
        return result;
    }

    /**
     * 将一条查询记录包装为一个对象
     *
     * @param clazz 包装类
     * @param row   查询记录
     *
     * @return 包装后的对象
     *
     * @throws IllegalAccessException    如果实例化包装类失败
     * @throws InstantiationException    如果实例化包装类失败
     * @throws NoSuchMethodException     如果实例化包装类失败
     * @throws InvocationTargetException 如果实例化包装类失败
     * @throws SQLException              如果读取 LOB 数据失败
     * @throws IOException               如果读取 LOB 数据失败
     */
    public static Object convertRow(Class clazz, Map<String, Object> row)
            throws IllegalAccessException, InstantiationException, SQLException, IOException,
            NoSuchMethodException, InvocationTargetException {

        // pojo 类必须有一个缺省的构造函数。
        Object result = clazz.getDeclaredConstructor().newInstance();

        for (String colName : row.keySet()) {
            String fieldName = getFieldName(colName);
            if (fieldName == null) {
                warn("无法获取字段" + colName + "的属性名");
                continue;
            }

            Class fieldType;
            try {
                fieldType = getFieldType(clazz, fieldName);
            } catch (IllegalAccessException e) {
                warn(clazz + " 中没有属性 '" + fieldName + "'");
                continue;
            }

            Object value = convertProperty(row.get(colName), fieldType);
            if (value != null) {
                try {
                    BeanUtil.setValue(result, fieldName, value);
                } catch (Exception e) {
                    warn("设置" + clazz + " 的属性 \"" + fieldName
                            + "\"(" + value.getClass().getName() + ")失败: " + e.toString());
                }
            }
        }
        return result;
    }

    private static Class getFieldType(Class clazz, String fieldName) throws IllegalAccessException {
        Class tracingType = clazz;

        while (tracingType != null && tracingType != Object.class) {
            try {
                Field field = tracingType.getDeclaredField(fieldName);
                if (field == null) {
                    throw new IllegalAccessException("找不到 " + clazz + " 的成员：" + fieldName);
                }
                return field.getType();
            } catch (NoSuchFieldException e) {
                tracingType = tracingType.getSuperclass();
            }
        }
        throw new IllegalAccessException("找不到 " + clazz + " 的成员：" + fieldName);
    }

    /**
     * 根据属性类型转换值对象。这里是从 Row 对象的属性值转换为 Bean 对象的属性值
     * <ol>
     * <li>当值对象为 {@link Timestamp} 时，将其转换为 {@link java.util.Date} 对象；</li>
     * </ol>
     *
     * @param o         对象
     * @param fieldType 属性类型
     *
     * @return 转换后的值
     *
     * @throws SQLException 如果数据库访问 LOB 字段失败
     * @throws IOException  如果从流中读取内容失败
     */
    private static Object convertProperty(Object o, Class fieldType) throws SQLException, IOException {

        if (fieldType == Boolean.TYPE) {
            String str = String.valueOf(o);
            if (str.matches("^\\-?\\d+\\.?\\d+$")) {    // 如果该字段存储的是数字
                return !"0".equals(str);
            } else {
                return "true".equalsIgnoreCase(str) || "yes".equalsIgnoreCase(str);
            }
        } else if (o == null) {
            return null;
        } else if (o instanceof Timestamp) {
            return new Date(((Timestamp) o).getTime());
        } else if (fieldType == String.class) {
            return convertToString(o);
        } else if (fieldType.isEnum() && o instanceof String) {
            return Enum.valueOf(fieldType, (String) o);
        } else {
            return o;
        }
    }

    private static String convertToString(Object o) {
        if (o instanceof Number) {
            return new BigDecimal(o.toString()).toString();
        } else {
            return o.toString();
        }
    }

    /**
     * 读取 lob 对象的值，并返回字符串
     *
     * @param lob 要读取的 Lob 对象
     *
     * @return 自字符串
     *
     * @throws SQLException 如果数据库访问 LOB 字段失败
     * @throws IOException  如果从流中读取内容失败
     */
    public static String readLobString(Object lob) throws SQLException, IOException {
        if (lob instanceof Clob) {
            return ClobUtil.read((Clob) lob);
        } else if (lob instanceof Blob) {
            return BlobReader.readString((Blob) lob, "Unicode");
        } else {
            LOG.warn("参数不是 lob 对象：" + lob.getClass());
            return "";
        }
    }

    /**
     * 打印警告信息。相同的警告只会打印一次。
     *
     * @param msg 警告信息
     */
    private static void warn(String msg) {
        List<String> list = warnedMsgs.get();
        if (list == null) {
            list = new ArrayList<>();
            warnedMsgs.set(list);
        }
        if (!list.contains(msg)) {
            LOG.warn(msg);
            list.add(msg);
        }
    }

    /**
     * 将字段名转换成属性名。该方法首先从缓存中取相应的属性名。如果取不到，则调用
     * {@link Str#columnToProperty(String)}
     * 方法获取属性名，并放入缓存。
     *
     * @param columnName 字段名
     *
     * @return 属性名
     */
    public static String getFieldName(String columnName) {
        if (convertBuffer.get(columnName) == null) {
            String fieldName = Str.columnToProperty(columnName);
            convertBuffer.put(columnName, fieldName);
        }
        return convertBuffer.get(columnName);
    }

}
