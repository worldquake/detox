package hu.detox.szexpartnerek.ws.rest;

import lombok.Getter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Getter
public class CsvListRowMapper implements RowMapper<List<Object>> {
    private List<String> headers;
    private List<List<Object>> values = new ArrayList<>();

    @Override
    public List<Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        if (rowNum == 0) {
            ResultSetMetaData meta = rs.getMetaData();
            headers = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String name = JdbcUtils.lookupColumnName(meta, i);
                headers.add(name);
            }
        }
        var ivals = new ArrayList<>();
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            Object val = JdbcUtils.getResultSetValue(rs, i);
            ivals.add(val);
        }
        values.add(ivals);
        return ivals;
    }
}