/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package au.com.bytecode.opencsv;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * helper class for processing JDBC ResultSet objects
 */
public class ResultSetHelperService implements ResultSetHelper {
    public static final int CLOBBUFFERSIZE = 2048;

    // note: we want to maintain compatibility with Java 5 VM's
    // These types don't exist in Java 5
    private static final int NVARCHAR = -9;
    private static final int NCHAR = -15;
    private static final int LONGNVARCHAR = -16;
    private static final int NCLOB = 2011;

    private static String read(Clob c) throws SQLException, IOException {
        StringBuilder sb = new StringBuilder((int) c.length());
        Reader r = c.getCharacterStream();
        char[] cbuf = new char[CLOBBUFFERSIZE];
        int n;
        while ((n = r.read(cbuf, 0, cbuf.length)) != -1) {
            sb.append(cbuf, 0, n);
        }
        return sb.toString();
    }

    public String[] getColumnNames(ResultSet rs) throws SQLException {
        List<String> names = new ArrayList<String>();
        ResultSetMetaData metadata = rs.getMetaData();

        for (int i = 0; i < metadata.getColumnCount(); i++) {
            names.add(metadata.getColumnName(i + 1));
        }

        String[] nameArray = new String[names.size()];
        return names.toArray(nameArray);
    }

    public String[] getColumnValues(ResultSet rs) throws SQLException, IOException {

        List<String> values = new ArrayList<String>();
        ResultSetMetaData metadata = rs.getMetaData();

        for (int i = 0; i < metadata.getColumnCount(); i++) {
            values.add(getColumnValue(rs, metadata.getColumnType(i + 1), i + 1));
        }

        String[] valueArray = new String[values.size()];
        return values.toArray(valueArray);
    }

    private String handleObject(Object obj) {
        return obj == null ? "" : String.valueOf(obj);
    }

    private String handleBigDecimal(BigDecimal decimal) {
        return decimal == null ? "" : decimal.toString();
    }

    private String handleLong(ResultSet rs, int columnIndex) throws SQLException {
        long lv = rs.getLong(columnIndex);
        return rs.wasNull() ? "" : Long.toString(lv);
    }

    private String handleInteger(ResultSet rs, int columnIndex) throws SQLException {
        int i = rs.getInt(columnIndex);
        return rs.wasNull() ? "" : Integer.toString(i);
    }

    private String handleDate(ResultSet rs, int columnIndex) throws SQLException {
        java.sql.Date date = rs.getDate(columnIndex);
        String value = null;
        if (date != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.US);
            value = dateFormat.format(date);
        }
        return value;
    }

    private String handleTime(Time time) {
        return time == null ? null : time.toString();
    }

    private String handleTimestamp(Timestamp timestamp) {
        SimpleDateFormat timeFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.US);
        return timestamp == null ? null : timeFormat.format(timestamp);
    }

    private String getColumnValue(ResultSet rs, int colType, int colIndex)
            throws SQLException, IOException {

        String value = "";

        switch (colType) {
            case Types.BIT:
            case Types.JAVA_OBJECT:
                value = handleObject(rs.getObject(colIndex));
                break;
            case Types.BOOLEAN:
                boolean b = rs.getBoolean(colIndex);
                value = Boolean.valueOf(b).toString();
                break;
            case NCLOB: // todo : use rs.getNClob
            case Types.CLOB:
                Clob c = rs.getClob(colIndex);
                if (c != null) {
                    value = read(c);
                }
                break;
            case Types.BIGINT:
                value = handleLong(rs, colIndex);
                break;
            case Types.DECIMAL:
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.REAL:
            case Types.NUMERIC:
                value = handleBigDecimal(rs.getBigDecimal(colIndex));
                break;
            case Types.INTEGER:
            case Types.TINYINT:
            case Types.SMALLINT:
                value = handleInteger(rs, colIndex);
                break;
            case Types.DATE:
                value = handleDate(rs, colIndex);
                break;
            case Types.TIME:
                value = handleTime(rs.getTime(colIndex));
                break;
            case Types.TIMESTAMP:
                value = handleTimestamp(rs.getTimestamp(colIndex));
                break;
            case NVARCHAR: // todo : use rs.getNString
            case NCHAR: // todo : use rs.getNString
            case LONGNVARCHAR: // todo : use rs.getNString
            case Types.LONGVARCHAR:
            case Types.VARCHAR:
            case Types.CHAR:
                value = rs.getString(colIndex);
                break;
            default:
                value = "";
        }


        if (value == null) {
            value = "";
        }

        return value;

    }
}
