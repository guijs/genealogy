package com.genealogy.config;

import com.genealogy.domain.family.Role;
import com.genealogy.domain.projection.Gender;
import com.genealogy.domain.projection.MarriageStatus;
import com.genealogy.domain.projection.ParentChildSubtype;
import com.genealogy.domain.projection.ParentRole;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Configuration
public class MyBatisConfig {

    @Bean
    public ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> {
            configuration.getTypeHandlerRegistry().register(UUID.class, new UUIDTypeHandler());
            configuration.getTypeHandlerRegistry().register(Role.class, new RoleTypeHandler());
            configuration.getTypeHandlerRegistry().register(Gender.class, new GenderTypeHandler());
            configuration.getTypeHandlerRegistry().register(MarriageStatus.class, new MarriageStatusTypeHandler());
            configuration.getTypeHandlerRegistry().register(ParentChildSubtype.class, new ParentChildSubtypeTypeHandler());
            configuration.getTypeHandlerRegistry().register(ParentRole.class, new ParentRoleTypeHandler());
            configuration.getTypeHandlerRegistry().register(StringArrayTypeHandler.class);
        };
    }

    @MappedTypes(UUID.class)
    public static class UUIDTypeHandler extends BaseTypeHandler<UUID> {
        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
            ps.setObject(i, parameter);
        }

        @Override
        public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
            Object obj = rs.getObject(columnName);
            if (obj == null) {
                return null;
            }
            if (obj instanceof UUID) {
                return (UUID) obj;
            }
            return UUID.fromString(obj.toString());
        }

        @Override
        public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            Object obj = rs.getObject(columnIndex);
            if (obj == null) {
                return null;
            }
            if (obj instanceof UUID) {
                return (UUID) obj;
            }
            return UUID.fromString(obj.toString());
        }

        @Override
        public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            Object obj = cs.getObject(columnIndex);
            if (obj == null) {
                return null;
            }
            if (obj instanceof UUID) {
                return (UUID) obj;
            }
            return UUID.fromString(obj.toString());
        }
    }

    @MappedTypes(Role.class)
    public static class RoleTypeHandler extends BaseTypeHandler<Role> {
        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, Role parameter, JdbcType jdbcType) throws SQLException {
            ps.setString(i, parameter.getValue());
        }

        @Override
        public Role getNullableResult(ResultSet rs, String columnName) throws SQLException {
            String value = rs.getString(columnName);
            return value == null ? null : fromValue(value);
        }

        @Override
        public Role getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            String value = rs.getString(columnIndex);
            return value == null ? null : fromValue(value);
        }

        @Override
        public Role getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            String value = cs.getString(columnIndex);
            return value == null ? null : fromValue(value);
        }

        private Role fromValue(String value) {
            for (Role role : Role.values()) {
                if (role.getValue().equals(value)) {
                    return role;
                }
            }
            return null;
        }
    }

    @MappedTypes(Gender.class)
    public static class GenderTypeHandler extends BaseTypeHandler<Gender> {
        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, Gender parameter, JdbcType jdbcType) throws SQLException {
            ps.setString(i, parameter.getValue());
        }

        @Override
        public Gender getNullableResult(ResultSet rs, String columnName) throws SQLException {
            String value = rs.getString(columnName);
            return value == null ? null : fromValue(value);
        }

        @Override
        public Gender getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            String value = rs.getString(columnIndex);
            return value == null ? null : fromValue(value);
        }

        @Override
        public Gender getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            String value = cs.getString(columnIndex);
            return value == null ? null : fromValue(value);
        }

        private Gender fromValue(String value) {
            for (Gender gender : Gender.values()) {
                if (gender.getValue().equals(value)) {
                    return gender;
                }
            }
            return null;
        }
    }

    @MappedTypes(MarriageStatus.class)
    public static class MarriageStatusTypeHandler extends BaseTypeHandler<MarriageStatus> {
        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, MarriageStatus parameter, JdbcType jdbcType) throws SQLException {
            ps.setString(i, parameter.getValue());
        }

        @Override
        public MarriageStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
            String value = rs.getString(columnName);
            return value == null ? null : fromValue(value);
        }

        @Override
        public MarriageStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            String value = rs.getString(columnIndex);
            return value == null ? null : fromValue(value);
        }

        @Override
        public MarriageStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            String value = cs.getString(columnIndex);
            return value == null ? null : fromValue(value);
        }

        private MarriageStatus fromValue(String value) {
            for (MarriageStatus status : MarriageStatus.values()) {
                if (status.getValue().equals(value)) {
                    return status;
                }
            }
            return null;
        }
    }

    @MappedTypes(ParentChildSubtype.class)
    public static class ParentChildSubtypeTypeHandler extends BaseTypeHandler<ParentChildSubtype> {
        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, ParentChildSubtype parameter, JdbcType jdbcType) throws SQLException {
            ps.setString(i, parameter.getValue());
        }

        @Override
        public ParentChildSubtype getNullableResult(ResultSet rs, String columnName) throws SQLException {
            String value = rs.getString(columnName);
            return value == null ? null : fromValue(value);
        }

        @Override
        public ParentChildSubtype getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            String value = rs.getString(columnIndex);
            return value == null ? null : fromValue(value);
        }

        @Override
        public ParentChildSubtype getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            String value = cs.getString(columnIndex);
            return value == null ? null : fromValue(value);
        }

        private ParentChildSubtype fromValue(String value) {
            for (ParentChildSubtype subtype : ParentChildSubtype.values()) {
                if (subtype.getValue().equals(value)) {
                    return subtype;
                }
            }
            return null;
        }
    }

    @MappedTypes(ParentRole.class)
    public static class ParentRoleTypeHandler extends BaseTypeHandler<ParentRole> {
        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, ParentRole parameter, JdbcType jdbcType) throws SQLException {
            ps.setString(i, parameter.getValue());
        }

        @Override
        public ParentRole getNullableResult(ResultSet rs, String columnName) throws SQLException {
            String value = rs.getString(columnName);
            return value == null ? null : fromValue(value);
        }

        @Override
        public ParentRole getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            String value = rs.getString(columnIndex);
            return value == null ? null : fromValue(value);
        }

        @Override
        public ParentRole getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            String value = cs.getString(columnIndex);
            return value == null ? null : fromValue(value);
        }

        private ParentRole fromValue(String value) {
            for (ParentRole role : ParentRole.values()) {
                if (role.getValue().equals(value)) {
                    return role;
                }
            }
            return null;
        }
    }

    @MappedTypes(List.class)
    public static class StringArrayTypeHandler extends BaseTypeHandler<List<String>> {
        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
            String[] array = parameter.toArray(new String[0]);
            Array sqlArray = ps.getConnection().createArrayOf("text", array);
            ps.setArray(i, sqlArray);
        }

        @Override
        public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
            return toList(rs.getArray(columnName));
        }

        @Override
        public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            return toList(rs.getArray(columnIndex));
        }

        @Override
        public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            return toList(cs.getArray(columnIndex));
        }

        private List<String> toList(Array array) throws SQLException {
            if (array == null) {
                return null;
            }
            Object[] arr = (Object[]) array.getArray();
            List<String> result = new ArrayList<>();
            for (Object obj : arr) {
                result.add(obj != null ? obj.toString() : null);
            }
            return result;
        }
    }
}
