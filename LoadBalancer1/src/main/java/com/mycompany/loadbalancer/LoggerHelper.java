package com.mycompany.loadbalancer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

public class LoggerHelper {
    
    private static final String LOG_FILE = "logs.txt";

    // ✅ Method to log events
    public static void log(String eventType, String message) {
        String timestamp = LocalDateTime.now().toString();
        String logEntry = "[" + timestamp + "] [" + eventType + "] " + message;

        // ✅ Print to console (optional)
        System.out.println(logEntry);

        // ✅ Write log entry to file
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(logEntry);
        } catch (IOException e) {
            System.out.println("❌ Error writing to log file: " + e.getMessage());
        }
    }
}
