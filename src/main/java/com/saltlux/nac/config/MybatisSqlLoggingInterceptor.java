package com.saltlux.nac.config;

import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class MybatisSqlLoggingInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(MybatisSqlLoggingInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (log.isInfoEnabled()) {
            Object[] args = invocation.getArgs();
            if (args != null && args.length >= 2 && args[0] instanceof MappedStatement mappedStatement) {
                Object parameterObject = args[1];
                BoundSql boundSql = mappedStatement.getBoundSql(parameterObject);
                log.info("MyBatis SQL | id={} | {}", mappedStatement.getId(), buildSql(mappedStatement, boundSql, parameterObject));
            }
        }
        return invocation.proceed();
    }

    private String buildSql(MappedStatement mappedStatement, BoundSql boundSql, Object parameterObject) {
        String sql = normalizeSql(boundSql.getSql());
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings == null || parameterMappings.isEmpty()) {
            return sql;
        }

        Configuration configuration = mappedStatement.getConfiguration();
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);

        for (ParameterMapping parameterMapping : parameterMappings) {
            Object value = resolveParameterValue(
                    parameterMapping.getProperty(),
                    boundSql,
                    parameterObject,
                    metaObject,
                    typeHandlerRegistry
            );
            sql = replaceFirstPlaceholder(sql, formatValue(value));
        }
        return sql;
    }

    private Object resolveParameterValue(String property,
                                         BoundSql boundSql,
                                         Object parameterObject,
                                         MetaObject metaObject,
                                         TypeHandlerRegistry typeHandlerRegistry) {
        if (boundSql.hasAdditionalParameter(property)) {
            return boundSql.getAdditionalParameter(property);
        }
        if (parameterObject == null) {
            return null;
        }
        if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            return parameterObject;
        }
        if (metaObject != null && metaObject.hasGetter(property)) {
            return metaObject.getValue(property);
        }
        return null;
    }

    private String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.replaceAll("\\s+", " ").trim();
    }

    private String replaceFirstPlaceholder(String sql, String value) {
        int index = sql.indexOf('?');
        if (index < 0) {
            return sql;
        }
        return sql.substring(0, index) + value + sql.substring(index + 1);
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Date || value instanceof TemporalAccessor) {
            return quote(value.toString());
        }
        if (value instanceof byte[]) {
            return "'<bytes>'";
        }
        return quote(value.toString());
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no properties
    }
}
