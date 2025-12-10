package com.mycompany.loadbalancer;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import com.jcraft.jsch.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import javafx.scene.control.Alert;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import javafx.application.Platform;

import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;


public class Primary1Controller {

    @FXML
    private ListView<String> fileListView;
    private List<File> selectedFiles = new ArrayList<>();

    @FXML
    private ChoiceBox<String> schedulingChoiceBox;
    @FXML
    private TextField commandInput;
    @FXML
    private TextArea terminalOutput;
    
    // ✅ System Performance Monitoring Labels
    @FXML
    private Label cpuUsageLabel;
    @FXML   
    private Label memoryUsageLabel;
    @FXML
    private Label transferSpeedLabel;
    @FXML
    private Label activeContainersLabel;


    // ✅ Add Remote Terminal UI Elements (New)
    @FXML
    private ChoiceBox<String> containerChoiceBox;
    @FXML
    private TextField remoteCommandInput;
    @FXML
    private TextArea remoteTerminalOutput;

    private static final String USERNAME = "ntu-user";
    private static final String PASSWORD = "ntu-user";
    private static final int SSH_PORT = 22;

    public List<File> getSelectedFiles() {
        return selectedFiles;
    }

    @FXML
    public void initialize() {
    // ✅ Add Scheduling Algorithms
    schedulingChoiceBox.getItems().addAll(
        "First-Come, First-Served (FCFS)", 
        "Shortest-Job-Next (SJN)", 
        "Round Robin"
    );
    schedulingChoiceBox.setValue("Round Robin");

    // ✅ Populate Available Containers for Remote Terminal
    containerChoiceBox.getItems().addAll(App.getHealthyContainers());

    // ✅ Start Monitoring System Performance (NEW)
    startMonitoring();
}


    @FXML
    private void handleBrowse() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files");
        List<File> files = fileChooser.showOpenMultipleDialog(null);

        if (files != null) {
            selectedFiles.clear();
            fileListView.getItems().clear();
            selectedFiles.addAll(files);
            for (File file : files) {
                fileListView.getItems().add(file.getAbsolutePath());
            }
        }
    }
    
    @FXML
    private void handleDownload() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Download File");
    dialog.setHeaderText("Enter the filename you want to download:");
    dialog.setContentText("Filename:");

    Optional<String> result = dialog.showAndWait();

    result.ifPresent(filename -> {
        if (filename.trim().isEmpty()) {
            LoggerHelper.log("ERROR", "❌ No filename entered.");
            return;
        }

        LoggerHelper.log("FILE_DOWNLOAD", "🔍 Searching for chunks of file: " + filename);

        boolean success = App.downloadFile(filename);
        if (success) {
            LoggerHelper.log("FILE_DOWNLOAD", "✅ Downloaded & Merged file successfully: " + filename);
            showDownloadSuccess(filename);
        } else {
            LoggerHelper.log("ERROR", "❌ Failed to download file: " + filename);
            showDownloadError(filename);
        }
    });
}


// ✅ Display success message
private void showDownloadSuccess(String filename) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Download Complete");
    alert.setHeaderText(null);
    alert.setContentText("✅ File '" + filename + "' has been downloaded successfully.");
    alert.showAndWait();
}

// ✅ Display error message
private void showDownloadError(String filename) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Download Failed");
    alert.setHeaderText(null);
    alert.setContentText("❌ File '" + filename + "' was not found in any container.");
    alert.showAndWait();
}

    @FXML
    private void handleExecuteCommand() {
        String command = commandInput.getText().trim();
        if (command.isEmpty()) {
            terminalOutput.setText("⚠ Please enter a command.");
            return;
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            output.append("\nExit Code: ").append(exitCode);
            terminalOutput.setText(output.toString());

        } catch (Exception e) {
            terminalOutput.setText("❌ Error executing command: " + e.getMessage());
        }
    }

        @FXML
    private void handleTransfer() throws IOException {
    if (!selectedFiles.isEmpty()) {
        LoggerHelper.log("FILE_TRANSFER", "📂 Starting file transfer...");

        List<String> sourceFiles = new ArrayList<>();
        List<String> destFiles = new ArrayList<>();

        for (File file : selectedFiles) {
            if (file.length() > 5 * 1024 * 1024) { // ✅ If file is >5MB, split it into chunks
                LoggerHelper.log("FILE_CHUNKING", "🔄 Splitting large file: " + file.getName());
                List<File> chunkedFiles = FileChunkHelper.splitFile(file, 5 * 1024 * 1024);
                for (File chunk : chunkedFiles) {
                    sourceFiles.add(chunk.getAbsolutePath());
                    destFiles.add("/home/ntu-user/" + chunk.getName());
                }
            } else { // ✅ If file is small, transfer it normally
                sourceFiles.add(file.getAbsolutePath());
                destFiles.add("/home/ntu-user/" + file.getName());
            }
        }

        String selectedAlgorithm = schedulingChoiceBox.getValue();
        List<String> healthyContainers = App.getHealthyContainers();

        if (healthyContainers.isEmpty()) {
            LoggerHelper.log("ERROR", "❌ No healthy containers available. Aborting transfer.");
            return;
        }

        App.transferFiles(sourceFiles, destFiles, selectedAlgorithm, healthyContainers);
        LoggerHelper.log("FILE_TRANSFER", "✅ File transfer completed using " + selectedAlgorithm);
        showSuccessMessage();
    } else {
        LoggerHelper.log("WARNING", "⚠ No files selected for transfer.");
    }
}


    private void showSuccessMessage() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("File Transfer");
        alert.setHeaderText(null);
        alert.setContentText("File transfer completed successfully!");
        alert.showAndWait();
    }

    @FXML
    private void handleCheckHealthyContainers() {
    List<String> healthyContainers = App.getHealthyContainers();  // Fetch only healthy containers

    if (healthyContainers.isEmpty()) {
        terminalOutput.setText("❌ No healthy containers found!");
        LoggerHelper.log("HEALTH_CHECK", "❌ No healthy containers found!");
    } else {
        StringBuilder message = new StringBuilder("✅ Healthy Containers:\n");
        for (String container : healthyContainers) {
            message.append(container).append("\n");
        }
        terminalOutput.setText(message.toString());

        // ✅ Log once instead of multiple times
        LoggerHelper.log("HEALTH_CHECK", "✅ Healthy containers checked: " + healthyContainers.size());
    }
}

    // ✅ New Method: Execute SSH Command Inside Selected Container
    @FXML
    private void handleExecuteRemoteCommand() {
        String selectedContainer = containerChoiceBox.getValue();
        String command = remoteCommandInput.getText().trim();

        if (selectedContainer == null || selectedContainer.isEmpty()) {
            remoteTerminalOutput.appendText("❌ Please select a container.\n");
            return;
        }

        if (command.isEmpty()) {
            remoteTerminalOutput.appendText("❌ Please enter a command.\n");
            return;
        }

        executeRemoteCommand(selectedContainer, command);
    }

    // ✅ New Method: SSH into Container & Execute Command
    private void executeRemoteCommand(String container, String command) {
        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(USERNAME, container, SSH_PORT);
                session.setPassword(PASSWORD);

                java.util.Properties config = new java.util.Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);
                session.connect(5000);

                ChannelExec channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(command);
                channel.setInputStream(null);
                channel.setErrStream(System.err);

                BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;

                channel.connect();

                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                channel.disconnect();
                session.disconnect();

                Platform.runLater(() -> {
                    remoteTerminalOutput.appendText("📡 Command Executed on " + container + ":\n" + output + "\n");
                });

                LoggerHelper.log("SSH_SESSION", "Executed command on " + container + ": " + command);
            } catch (Exception e) {
                Platform.runLater(() -> remoteTerminalOutput.appendText("❌ Error executing command: " + e.getMessage() + "\n"));
                LoggerHelper.log("ERROR", "Failed to execute command on " + container + ": " + e.getMessage());
            }
        }).start();
    }
    private void startMonitoring() {
    Timer timer = new Timer(true); // Run as a background daemon thread
    timer.scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
            updatePerformanceMetrics();
        }
    }, 0, 5000); // Update every 5 seconds
}

    private void updatePerformanceMetrics() {
    Platform.runLater(() -> {
        cpuUsageLabel.setText(getCPUUsage() + " %");
        memoryUsageLabel.setText(getMemoryUsage() + " MB");
        transferSpeedLabel.setText(getTransferSpeed() + " MB/s");
        activeContainersLabel.setText(String.valueOf(App.getHealthyContainers().size())); // ✅ Just show the count
    });
}

 // get cpu usage
   private double getCPUUsage() {
    OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    // Returns system CPU load (0.0 to 1.0), convert to percentage
    double cpuLoad = osBean.getSystemCpuLoad() * 100;
    
    if (cpuLoad < 0) {
        return 0.0; // Return 0 if CPU usage is not available
    }

    return Math.round(cpuLoad * 10.0) / 10.0; // Round to 1 decimal place
}


// ✅ Get Memory Usage (MB)
private long getMemoryUsage() {
    Runtime runtime = Runtime.getRuntime();
    return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024); // Convert bytes to MB
}

// ✅ Variables for Tracking File Transfer Speed
private long lastTransferTime = System.currentTimeMillis();
private long lastTransferredBytes = 0;


// ✅ Calculate File Transfer Speed (MB/s)
private String getTransferSpeed() {
    long currentTime = System.currentTimeMillis();
    long elapsedTime = (currentTime - lastTransferTime) / 1000; // Convert to seconds

    if (elapsedTime == 0) return "Waiting...";

    long transferredBytes = App.getTransferredBytes(); // Get transferred bytes from App.java
    long speed = (transferredBytes - lastTransferredBytes) / elapsedTime / (1024 * 1024); // Convert to MB/s

    lastTransferTime = currentTime;
    lastTransferredBytes = transferredBytes;

    return speed + " MB/s";
}

// ✅ Get Active Containers Count
private int getActiveContainersCount() {
    List<String> healthyContainers = App.getHealthyContainers();
    return healthyContainers.size();
}

}
