module com.mycompany.loadbalancer {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires jsch;
    requires java.base;
    requires java.management; // ✅ Enables access to ManagementFactory
    requires jdk.management;  // ✅ Enables access to com.sun.management APIs
    opens com.mycompany.loadbalancer to javafx.fxml;
    exports com.mycompany.loadbalancer;
}
    
//yash