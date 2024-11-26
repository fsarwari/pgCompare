/*
 * Copyright 2012-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crunchydata.util;

import com.crunchydata.services.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

import static com.crunchydata.services.dbDB2.columnValueMapDB2;
import static com.crunchydata.services.dbMSSQL.columnValueMapMSSQL;
import static com.crunchydata.services.dbMySQL.columnValueMapMySQL;
import static com.crunchydata.services.dbOracle.columnValueMapOracle;
import static com.crunchydata.services.dbPostgres.columnValueMapPostgres;
import static com.crunchydata.util.DataUtility.preserveCase;
import static com.crunchydata.util.SQLConstantsDB2.SQL_DB2_SELECT_COLUMNS;
import static com.crunchydata.util.SQLConstantsMSSQL.SQL_MSSQL_SELECT_COLUMNS;
import static com.crunchydata.util.SQLConstantsMYSQL.SQL_MYSQL_SELECT_COLUMNS;
import static com.crunchydata.util.SQLConstantsOracle.SQL_ORACLE_SELECT_COLUMNS;
import static com.crunchydata.util.SQLConstantsPostgres.SQL_POSTGRES_SELECT_COLUMNS;
import static com.crunchydata.util.Settings.Props;

/**
 * Utility class for column data type validation and classification.
 *
 * <p>Provides methods to determine the classification of a given database column data type.</p>
 *
 * @author Brian Pace
 */
public class ColumnUtility {

    private static final String THREAD_NAME = "columnUtility";

    /**
     * Array of data types classified as boolean.
     */
    public static String[] booleanTypes = new String[]{"bool", "boolean"};

    /**
     * Array of data types classified as character.
     */
    public static String[] charTypes = new String[]{"bpchar", "char", "character", "clob", "json", "jsonb", "nchar", "nclob",
            "ntext", "nvarchar", "nvarchar2", "text", "varchar", "varchar2", "xml"};

    /**
     * Array of data types classified as numeric.
     */
    public static String[] numericTypes = new String[]{"bigint", "bigserial", "binary_double", "binary_float", "dec",
            "decimal", "double", "double precision", "fixed", "float", "float4", "float8", "int", "integer", "int2",
            "int4", "int8", "money", "number", "numeric", "real", "serial", "smallint", "smallmoney", "smallserial",
            "tinyint"};

    /**
     * Array of data types classified as timestamp.
     */
    public static String[] timestampTypes = new String[]{"date", "datetime", "datetimeoffset", "datetime2",
            "smalldatetime", "time", "timestamp", "timestamptz", "timestamp(0)", "timestamp(1) with time zone",
            "timestamp(3)", "timestamp(3) with time zone", "timestamp(6)", "timestamp(6) with time zone",
            "timestamp(9)", "timestamp(9) with time zone", "year"};

    /**
     * Array of data types classified as binary.
     */
    public static String[] binaryTypes = new String[]{"bytea", "binary", "blob", "raw", "varbinary"};

    /**
     * Array of unsupported data types.
     */
    public static String[] unsupportedDataTypes = new String[]{"bfile", "bit", "cursor", "enum", "hierarchyid",
            "image", "rowid", "rowversion", "set", "sql_variant", "uniqueidentifier", "long", "long raw"};

    /**
     * Array of reserved words
     */
    public static String[] reservedWords = new String[]{"add", "all", "alter", "and", "any", "as", "asc", "at", "authid", "between", "by",
                                                        "character", "check", "cluster", "column", "comment", "connect", "constraint", "continue",
                                                        "create", "cross", "current", "current_user", "cursor", "database", "date", "default",
                                                        "delete", "desc", "distinct", "double", "else", "end", "except", "exception", "exists",
                                                        "external", "fetch", "for", "from", "grant", "group", "having", "identified", "if", "in",
                                                        "index", "insert", "integer", "intersect", "into", "is", "join", "like", "lock", "long", "loop",
                                                        "modify", "natural", "no", "not", "null", "on", "open", "option", "or", "order", "outer",
                                                        "package", "prior", "privileges", "procedure", "public", "rename", "replace", "rowid", "rownum",
                                                        "schema", "select", "session", "set", "sql", "start", "statement", "sys", "table", "then", "to",
                                                        "trigger", "union", "unique", "update", "values", "view", "varchar", "varchar2", "view", "when",
                                                        "where", "with", "xor", "user"};


    /**
     * Retrieves column metadata for a specified table in Postgres database.
     *
     * @param conn      Database connection to Postgres server.
     * @param schema    Schema name of the table.
     * @param table     Table name.
     * @param destRole  Role of the database (source/target)
     * @return JSONArray containing metadata for each column in the table.
     */
    public static JSONArray getColumns (Connection conn, String schema, String table, String destRole) {
        JSONArray columnInfo = new JSONArray();
        String nativeCase = switch (Props.getProperty(destRole + "-type")) {
            case "oracle" -> dbOracle.nativeCase;
            case "mysql" -> dbMySQL.nativeCase;
            case "mssql" -> dbMSSQL.nativeCase;
            case "db2" -> dbDB2.nativeCase;
            default -> dbPostgres.nativeCase;
        };
        String columnSQL = switch (Props.getProperty(destRole + "-type")) {
            case "oracle" -> SQL_ORACLE_SELECT_COLUMNS;
            case "mysql" -> SQL_MYSQL_SELECT_COLUMNS;
            case "mssql" -> SQL_MSSQL_SELECT_COLUMNS;
            case "db2" -> SQL_DB2_SELECT_COLUMNS;
            default -> SQL_POSTGRES_SELECT_COLUMNS;
        };


                try {
                    PreparedStatement stmt = conn.prepareStatement(columnSQL);
                    stmt.setObject(1, schema);
                    stmt.setObject(2, table);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        JSONObject column = new JSONObject();
                        if (Arrays.asList(unsupportedDataTypes).contains(rs.getString("data_type").toLowerCase())) {
                            Logging.write("warning", THREAD_NAME, String.format("Unsupported data type (%s) for column (%s)", rs.getString("data_type"), rs.getString("column_name")));
                            //System.exit(1);
                            column.put("supported", false);
                        } else {
                            column.put("supported", true);
                        }
                        column.put("columnName", rs.getString("column_name"));
                        column.put("dataType", rs.getString("data_type"));
                        column.put("dataLength", rs.getInt("data_length"));
                        column.put("dataPrecision", rs.getInt("data_precision"));
                        column.put("dataScale", rs.getInt("data_scale"));
                        column.put("nullable", rs.getString("nullable").equals("Y"));
                        column.put("primaryKey", rs.getString("pk").equals("Y"));
                        column.put("dataClass", getDataClass(rs.getString("data_type").toLowerCase()));
                        column.put("preserveCase", preserveCase(nativeCase, rs.getString("column_name")));

                        String valueExpression = switch (Props.getProperty(destRole + "-type")) {
                            case "oracle" -> columnValueMapOracle(column);
                            case "mysql" -> columnValueMapMySQL(column);
                            case "mssql" -> columnValueMapMSSQL(column);
                            case "db2" -> columnValueMapDB2(column);
                            default -> columnValueMapPostgres(column);
                        };

                        column.put("valueExpression", valueExpression);

                        columnInfo.put(column);
                    }
                    rs.close();
                    stmt.close();
                } catch (Exception e) {
                    Logging.write("severe", THREAD_NAME, String.format("Error retrieving columns for table %s.%s:  %s", schema, table, e.getMessage()));
                }
                return columnInfo;
    }

    /**
     * Returns the classification of a given database column data type.
     *
     * <p>The method checks the provided dataType against predefined arrays of data types and returns
     * a classification string.</p>
     *
     * <p>If the dataType does not match any predefined type, it defaults to "char".</p>
     *
     * @param dataType The database column data type to classify.
     * @return A string representing the classification of the data type ("boolean", "numeric", "char").
     */
    public static String getDataClass(String dataType) {
        // Default classification is "char"
        String dataClass = "char";

        if (Arrays.asList(booleanTypes).contains(dataType.toLowerCase())) {
            dataClass = "boolean";
        }

        if (Arrays.asList(numericTypes).contains(dataType.toLowerCase())) {
            dataClass = "numeric";
        }

        if (Arrays.asList(timestampTypes).contains(dataType.toLowerCase())) {
            dataClass = "char";
        }

        return dataClass;
    }

}
