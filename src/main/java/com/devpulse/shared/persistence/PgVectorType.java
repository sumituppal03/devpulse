package com.devpulse.shared.persistence;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

/**
 * Maps a Java String (e.g. "[0.12,-0.87,...]") onto a Postgres pgvector column.
 *
 * columnDefinition = "vector(768)" on @Column only affects DDL generation —
 * it has no effect on how Hibernate binds the value at insert/update time.
 * Left alone, Hibernate treats the field as a plain String and sends it as
 * JDBC VARCHAR, which Postgres refuses to implicitly cast to vector:
 *
 *   ERROR: column "embedding" is of type vector but expression is of type
 *   character varying
 *
 * Binding with Types.OTHER instead sends the value as an untyped ("unknown")
 * parameter, the same way a plain string literal behaves in raw SQL — Postgres
 * then infers the correct type (vector) from the target column, exactly like
 * it would for `INSERT INTO code_chunks (embedding) VALUES ('[0.1,0.2]')`.
 */
public class PgVectorType implements UserType<String> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<String> returnedClass() {
        return String.class;
    }

    @Override
    public boolean equals(String x, String y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(String x) {
        return Objects.hashCode(x);
    }

    @Override
    public String nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        return rs.getString(position);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, String value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, value, Types.OTHER);
        }
    }

    @Override
    public String deepCopy(String value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(String value) {
        return value;
    }

    @Override
    public String assemble(Serializable cached, Object owner) {
        return (String) cached;
    }
}