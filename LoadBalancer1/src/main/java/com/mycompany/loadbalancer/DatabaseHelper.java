package com.mycompany.loadbalancer;

import java.sql.*;
import java.util.Timer;
import java.util.TimerTask;

public class DatabaseHelper {

    // ✅ SQLite Database Connection
    private static final String SQLITE_URL = "jdbc:sqlite:loadbalancer.db";

    // ✅ Remote MySQL Database Connection
    private static final String MYSQL_URL = "jdbc:mysql://lamp-server:3306/loadbalancer_db";
    private static final String MYSQL_USER = "admin";
    private static final String MYSQL_PASSWORD = "wpzL2JsQ2qVR";

    
   private static Connection sqliteConnection = null;
    private static Connection mysqlConnection = null;

// ✅ Connect to SQLite (Reuse connection)
public static Connection connectSQLite() {
    try {
        if (sqliteConnection == null || sqliteConnection.isClosed()) {
            sqliteConnection = DriverManager.getConnection(SQLITE_URL);
            LoggerHelper.log("DATABASE", "Connected to SQLite database successfully.");
        }
    } catch (SQLException e) {
        LoggerHelper.log("ERROR", "❌ SQLite Connection Error: " + e.getMessage());
    }
    return sqliteConnection;
}

// ✅ Connect to MySQL (Reuse connection)
public static Connection connectMySQL() {
    try {
        if (mysqlConnection == null || mysqlConnection.isClosed()) {
            mysqlConnection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
            LoggerHelper.log("DATABASE", "Connected to MySQL database successfully.");
        }
    } catch (SQLException e) {
        LoggerHelper.log("ERROR", "❌ MySQL Connection Error: " + e.getMessage());
    }
    return mysqlConnection;
}


    // ✅ Create Tables in Both Databases (Java 8 Compatible)
    public static void createTables() {
        // Table for File Metadata
        String createFilesTable = "CREATE TABLE IF NOT EXISTS file_metadata ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "filename TEXT NOT NULL, "
            + "file_size INTEGER NOT NULL, "
            + "transfer_status TEXT DEFAULT 'PENDING', "
            + "transfer_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
            + ");";

        // Table for User Sessions
        String createUsersTable = "CREATE TABLE IF NOT EXISTS user_sessions ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "username TEXT NOT NULL, "
            + "login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "session_status TEXT DEFAULT 'ACTIVE'"
            + ");";

        // Adjust syntax for MySQL
        String createFilesTableMySQL = createFilesTable
            .replace("AUTOINCREMENT", "AUTO_INCREMENT")
            .replace("TEXT", "VARCHAR(255)")
            .replace("INTEGER", "BIGINT");

        String createUsersTableMySQL = createUsersTable
            .replace("AUTOINCREMENT", "AUTO_INCREMENT")
            .replace("TEXT", "VARCHAR(255)");

        // ✅ Create Tables in SQLite
try (Connection conn = connectSQLite(); Statement stmt = conn.createStatement()) {
    stmt.execute(createFilesTable);
    stmt.execute(createUsersTable);
    LoggerHelper.log("DATABASE", "SQLite Tables Created Successfully.");
    
} catch (SQLException e) {
    LoggerHelper.log("ERROR", "SQLite Error Creating Tables: " + e.getMessage());
}

// ✅ Create Tables in MySQL
try (Connection conn = connectMySQL(); Statement stmt = conn.createStatement()) {
    stmt.execute(createFilesTableMySQL);
    stmt.execute(createUsersTableMySQL);
    LoggerHelper.log("DATABASE", "MySQL Tables Created Successfully.");
    
} catch (SQLException e) {
    LoggerHelper.log("ERROR", "MySQL Error Creating Tables: " + e.getMessage());
}
    }


    public static void storeFileMetadata(String filename, long fileSize) {
    String sql = "INSERT INTO file_metadata (filename, file_size, transfer_status) VALUES (?, ?, 'PENDING')";

    // ✅ Store in SQLite
    try (Connection conn = connectSQLite();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, filename);
        pstmt.setLong(2, fileSize);
        pstmt.executeUpdate();
        LoggerHelper.log("DATABASE", "✅ File metadata stored in SQLite: " + filename);
    } catch (SQLException e) {
        LoggerHelper.log("ERROR", "❌ SQLite Error storing file metadata: " + e.getMessage());
    }

    // ✅ Store in MySQL
    try (Connection conn = connectMySQL();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, filename);
        pstmt.setLong(2, fileSize);
        pstmt.executeUpdate();
        LoggerHelper.log("DATABASE", "✅ File metadata stored in MySQL: " + filename);
    } catch (SQLException e) {
        LoggerHelper.log("ERROR", "❌ MySQL Error storing file metadata: " + e.getMessage());
    }
}



    
    public static void storeUserSession(String username) {
    String sql = "INSERT INTO user_sessions (username) VALUES (?)";

    // Store in SQLite
    try (Connection conn = connectSQLite();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, username);
        pstmt.executeUpdate();
        LoggerHelper.log("USER_SESSION", "User '" + username + "' session stored in SQLite.");
    } catch (SQLException e) {
        LoggerHelper.log("ERROR", "Failed to store user session (SQLite): " + e.getMessage());
    }

    // Store in MySQL
    try (Connection conn = connectMySQL();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, username);
        pstmt.executeUpdate();
        LoggerHelper.log("USER_SESSION", "User '" + username + "' session stored in MySQL.");
    } catch (SQLException e) {
        LoggerHelper.log("ERROR", "Failed to store user session (MySQL): " + e.getMessage());
    }
}

       



    // ✅ Start Auto Sync Every 30 Minutes
    public static void startAutoSync() {
        Timer timer = new Timer(true); // Runs in the background
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                syncDatabases();
            }
        }, 0, 30 * 60 * 1000); // Every 30 minutes
    }

    // ✅ Compare SQLite & MySQL and Update MySQL
    public static void syncDatabases() {
        LoggerHelper.log("DATABASE_SYNC", "🔄 Syncing MySQL with SQLite...");

        String fetchSQLiteData = "SELECT filename, file_size, transfer_status, transfer_time FROM file_metadata";
        String updateOrInsertMySQL = "INSERT INTO file_metadata (filename, file_size, transfer_status, transfer_time) "
    + "VALUES (?, ?, ?, ?) "
    + "ON DUPLICATE KEY UPDATE "
    + "file_size = VALUES(file_size), "
    + "transfer_status = VALUES(transfer_status), "
    + "transfer_time = VALUES(transfer_time);";


        try (Connection sqliteConn = connectSQLite();
             Connection mysqlConn = connectMySQL();
             PreparedStatement sqliteStmt = sqliteConn.prepareStatement(fetchSQLiteData);
             PreparedStatement mysqlStmt = mysqlConn.prepareStatement(updateOrInsertMySQL);
             ResultSet rs = sqliteStmt.executeQuery()) {

            while (rs.next()) {
                mysqlStmt.setString(1, rs.getString("filename"));
                mysqlStmt.setLong(2, rs.getLong("file_size"));
                mysqlStmt.setString(3, rs.getString("transfer_status"));
                mysqlStmt.setTimestamp(4, rs.getTimestamp("transfer_time"));
                mysqlStmt.executeUpdate();
            }

            LoggerHelper.log("DATABASE_SYNC", "✅ MySQL database updated successfully.");
        } catch (SQLException e) {
            LoggerHelper.log("ERROR", "❌ Error syncing databases: " + e.getMessage());
        }
    }




    // ✅ Update File Transfer Status in Both Databases
    public static void updateFileTransferStatus(String filename, String status) {
    String sql = "UPDATE file_metadata SET transfer_status = ? WHERE filename = ?";

    // ✅ Update in SQLite
    try (Connection conn = connectSQLite();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, status);
        pstmt.setString(2, filename);
        pstmt.executeUpdate();
        LoggerHelper.log("DATABASE", "✅ Transfer status updated in SQLite: " + filename + " -> " + status);
    } catch (SQLException e) {
        LoggerHelper.log("ERROR", "❌ SQLite Error updating transfer status: " + e.getMessage());
    }

    // ✅ Update in MySQL
    try (Connection conn = connectMySQL();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, status);
        pstmt.setString(2, filename);
        pstmt.executeUpdate();
        LoggerHelper.log("DATABASE", "✅ Transfer status updated in MySQL: " + filename + " -> " + status);
    } catch (SQLException e) {
        LoggerHelper.log("ERROR", "❌ MySQL Error updating transfer status: " + e.getMessage());
    }
}


}
