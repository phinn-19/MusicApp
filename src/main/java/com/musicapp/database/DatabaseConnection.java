package com.musicapp.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/musicapp";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "123456"; // đổi thành password MySQL của bạn

    private static Connection connection = null;

    public static Connection getConnection() {
        if (connection == null) {
            try {
                connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
                System.out.println("Kết nối database thành công!");
            } catch (SQLException e) {
                System.out.println("Lỗi kết nối database: " + e.getMessage());
            }
        }
        return connection;
    }
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
            } catch (SQLException e) {
                System.out.println("Lỗi đóng kết nối: " + e.getMessage());
            }
        }
    }
}