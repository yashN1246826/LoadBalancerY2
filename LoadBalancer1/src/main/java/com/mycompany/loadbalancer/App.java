package com.mycompany.loadbalancer;

import com.jcraft.jsch.*;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.Random;


public class App extends Application {

    private static final String SOURCE_HOST = "ntu-vm-comp20081";
    private static final String DEST_HOST = "ntu-load-balancer";
    private static final String USERNAME = "ntu-user";
    private static final String PASSWORD = "ntu-user";
    private static final int REMOTE_PORT = 22;
    private static final int SESSION_TIMEOUT = 10000;
    private static final int CHANNEL_TIMEOUT = 5000;
    private static final String[] DEST_CONTAINERS = {
    "comp20081-files-container1", "comp20081-files-container2", "comp20081-files-container3",
    "comp20081-files-container4", "comp20081-files-container5", "comp20081-files-container6",
    "comp20081-files-container7", "comp20081-files-container8"
};
    // File Locking Mechanism

    /**
     *
     */
public static final java.util.Map<String, ReentrantLock> fileLocks = new java.util.concurrent.ConcurrentHashMap<>();

public static ReentrantLock getFileLock(String filePath) {
    return fileLocks.computeIfAbsent(filePath, k -> new ReentrantLock());
}



   public static void main(String[] args) {
    DatabaseHelper.createTables(); // Initialize Database
    DatabaseHelper.storeUserSession("admin"); // Store user session at startup
    DatabaseHelper.startAutoSync();
    launch(args);
}


    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("primary1.fxml"));
            primaryStage.setScene(new Scene(loader.load()));
            primaryStage.setTitle("File Transfer");
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static long transferredBytes = 0;

    public static void updateTransferredBytes(long bytes) {
    transferredBytes += bytes;
}

    public static long getTransferredBytes() {
    return transferredBytes;
}

    
    

   



public static void transferFiles(List<String> sourceFiles, List<String> destFiles, String algorithm, List<String> healthyContainers) throws IOException {
    if (healthyContainers.isEmpty()) {
        LoggerHelper.log("ERROR", "❌ No healthy containers available. Aborting transfer.");
        return;
    }

    List<String> sortedSourceFiles = new ArrayList<>(sourceFiles);
    List<String> sortedDestFiles = new ArrayList<>(destFiles);

    switch (algorithm) {
        case "First-Come, First-Served (FCFS)":
            break;
        case "Shortest-Job-Next (SJN)":
            sortByFileSize(sortedSourceFiles, sortedDestFiles);
            break;
        case "Round Robin":
            sortedSourceFiles = roundRobinOrdering(sourceFiles);
            sortedDestFiles = roundRobinOrdering(destFiles);
            break;
        default:
            LoggerHelper.log("ERROR", "Invalid scheduling algorithm selected. Proceeding with FCFS.");
            break;
    }

    JSch jsch = new JSch();
    Random random = new Random();
    int totalFiles = sortedSourceFiles.size();
    double trafficCoefficient;

    if (totalFiles < 5) {
        trafficCoefficient = 0.8; // Low Traffic
    } else if (totalFiles <= 10) {
        trafficCoefficient = 1.2; // Medium Traffic
    } else {
        trafficCoefficient = 1.8; // High Traffic
    }

    System.out.println("🌍 Current Traffic Level: " + (totalFiles < 5 ? "LOW" : totalFiles <= 10 ? "MEDIUM" : "HIGH"));
    System.out.println("🚦 Applying Coefficient: " + trafficCoefficient);

    Session sourceSession = null;
    ChannelSftp sourceSftp = null;
    Session loadBalancerSession = null;
    ChannelSftp loadBalancerSftp = null;

    try {
        sourceSession = connectToHost(jsch, "ntu-vm-comp20081");
        sourceSftp = (ChannelSftp) sourceSession.openChannel("sftp");
        sourceSftp.connect(CHANNEL_TIMEOUT);

        loadBalancerSession = connectToHost(jsch, "ntu-load-balancer");
        loadBalancerSftp = (ChannelSftp) loadBalancerSession.openChannel("sftp");
        loadBalancerSftp.connect(CHANNEL_TIMEOUT);

        for (int i = 0; i < sortedSourceFiles.size(); i++) {
            if (healthyContainers.isEmpty()) {
                LoggerHelper.log("ERROR", "❌ No healthy servers available. Stopping transfer.");
                break;
            }

            String sourceFile = sortedSourceFiles.get(i);
            ReentrantLock lock = getFileLock(sourceFile);
            if (lock.tryLock()) {
                try {
                    // ✅ Check if file needs chunking
                    File file = new File(sourceFile);
                    List<String> fileChunks = new ArrayList<>();

                    if (file.length() > 5 * 1024 * 1024) { // File size > 5MB
                        LoggerHelper.log("FILE_CHUNKING", "🔄 Splitting large file: " + file.getName());
                        List<File> chunkedFiles = FileChunkHelper.splitFile(file, 5 * 1024 * 1024);
                        for (File chunk : chunkedFiles) {
                            fileChunks.add(chunk.getAbsolutePath());
                        }
                    } else {
                        fileChunks.add(sourceFile); // If file is small, add it as is
                    }

                    for (String chunk : fileChunks) {
    // ✅ Store each chunk's metadata before transferring
    DatabaseHelper.storeFileMetadata(new File(chunk).getName(), new File(chunk).length());

    // ✅ Apply Artificial Delay with Traffic Coefficient
    int baseDelay = 5 + random.nextInt(6); // 5-10 sec
    int adjustedDelay = (int) (baseDelay * trafficCoefficient); // Adjusted delay
    LoggerHelper.log("NETWORK", "Simulating network delay: " + adjustedDelay + " seconds...");
    TimeUnit.SECONDS.sleep(adjustedDelay); // Pause execution

    String tempFile = "tempfile_" + i + ".txt";
    String loadBalancerPath = "/home/ntu-user/" + new File(chunk).getName();

    // ✅ Read chunk content and encrypt before storing
    String chunkContent = new String(Files.readAllBytes(Paths.get(chunk)));
    String encryptedChunk = EncryptionHelper.encrypt(chunkContent);

    // ✅ Save encrypted content to temp file
    Files.write(Paths.get(tempFile), encryptedChunk.getBytes());

    LoggerHelper.log("FILE_TRANSFER", "🔒 Encrypting and Storing Chunk: " + chunk);

    // ✅ Upload encrypted chunk to Load Balancer
    LoggerHelper.log("FILE_TRANSFER", "🔒 Uploading Encrypted Chunk to Load Balancer");
    loadBalancerSftp.put(tempFile, loadBalancerPath); // Upload (encrypted)
    LoggerHelper.log("FILE_TRANSFER", "Transferred to Load Balancer: " + loadBalancerPath);

    int containerIndex = i % healthyContainers.size(); // Use only healthy servers
    String selectedContainer = healthyContainers.get(containerIndex);
    Session containerSession = connectToHost(jsch, selectedContainer);
    ChannelSftp containerSftp = (ChannelSftp) containerSession.openChannel("sftp");
    containerSftp.connect(CHANNEL_TIMEOUT);
    String destFile = "/home/ntu-user/" + new File(chunk).getName();

    LoggerHelper.log("FILE_TRANSFER", "🔒 Storing Encrypted Chunk in Container");
    containerSftp.put(tempFile, destFile); // Store (encrypted)
    LoggerHelper.log("FILE_TRANSFER", "✅ Transferred to " + selectedContainer);

    // ✅ Update the database after chunk is fully transferred
    DatabaseHelper.updateFileTransferStatus(new File(chunk).getName(), "COMPLETED");

    long fileSize = new File(chunk).length();
    App.updateTransferredBytes(fileSize); // ✅ Track file size in performance monitoring

    containerSftp.disconnect();
    containerSession.disconnect();
}



                } finally {
                    lock.unlock();
                }
            } else {
                 LoggerHelper.log("ERROR", "❌ File is currently locked, skipping: " + sourceFile);
            }
        }
    } catch (JSchException | SftpException | InterruptedException e) {
        e.printStackTrace();
    } finally {
        if (sourceSftp != null) sourceSftp.disconnect();
        if (sourceSession != null) sourceSession.disconnect();
        if (loadBalancerSftp != null) loadBalancerSftp.disconnect();
        if (loadBalancerSession != null) loadBalancerSession.disconnect();
    }
}


    public static boolean downloadFile(String filename) {
    JSch jsch = new JSch();
    String destinationDir = "/home/ntu-user/Downloads/DownloadedFiles/";
    List<File> chunks = new ArrayList<>();

    for (String container : DEST_CONTAINERS) {
        LoggerHelper.log("FILE_DOWNLOAD", "🔎 Checking in container: " + container);

        try {
            Session session = connectToHost(jsch, container);
            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");

            // List all chunks related to the requested file
            String checkCommand = "ls /home/ntu-user/ | grep '^" + filename + "_Chunk'";
            channelExec.setCommand(checkCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(channelExec.getInputStream()));
            channelExec.connect();

            String chunkName;
            while ((chunkName = reader.readLine()) != null) {
                LoggerHelper.log("FILE_DOWNLOAD", "✅ Found chunk: " + chunkName + " in " + container);
                String chunkPath = destinationDir + chunkName;
                
                boolean fetched = fetchFileFromContainer(jsch, container, chunkName, chunkPath);
                if (fetched) {
                    LoggerHelper.log("FILE_DOWNLOAD", "🔓 Decrypting Chunk: " + chunkName);

                    // ✅ Read the encrypted content and decrypt it
                    String encryptedContent = new String(Files.readAllBytes(Paths.get(chunkPath)));
                    String decryptedContent = EncryptionHelper.decrypt(encryptedContent);

                    // ✅ Save decrypted content back to file
                    Files.write(Paths.get(chunkPath), decryptedContent.getBytes());

                    LoggerHelper.log("FILE_DOWNLOAD", "✅ Chunk Decrypted Successfully");

                    chunks.add(new File(chunkPath));
                }
            }

            channelExec.disconnect();
            session.disconnect();
        } catch (Exception e) {
            LoggerHelper.log("ERROR", "❌ Error searching in " + container + ": " + e.getMessage());
        }
    }

    if (chunks.isEmpty()) {
        LoggerHelper.log("ERROR", "❌ No chunks found for file: " + filename);
        return false;
    }

    // ✅ Merge chunks into the final file
    return mergeChunks(chunks, destinationDir + filename);
}
   

    private static boolean mergeChunks(List<File> chunks, String finalFilePath) {
    try (FileOutputStream fos = new FileOutputStream(finalFilePath);
         BufferedOutputStream mergingStream = new BufferedOutputStream(fos)) {

        for (File chunk : chunks) {
            LoggerHelper.log("FILE_DOWNLOAD", "🔗 Merging chunk: " + chunk.getName());
            Files.copy(chunk.toPath(), mergingStream);
            chunk.delete(); // Clean up chunk after merging
        }

        LoggerHelper.log("FILE_DOWNLOAD", "✅ File merged successfully: " + finalFilePath);
        return true;
    } catch (IOException e) {
        LoggerHelper.log("ERROR", "❌ Failed to merge chunks: " + e.getMessage());
        return false;
    }
}


    
    
    
    
// ✅ Method to copy the file from the found container
private static boolean fetchFileFromContainer(JSch jsch, String container, String filename, String destinationPath) {
    try {
        LoggerHelper.log("FILE_DOWNLOAD", "📡 Connecting to " + container + " for file: " + filename);
        Session session = connectToHost(jsch, container);
        ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
        channelSftp.connect(CHANNEL_TIMEOUT);

        String containerFilePath = "/home/ntu-user/" + filename;
        LoggerHelper.log("FILE_DOWNLOAD", "📂 Checking file path: " + containerFilePath);
        LoggerHelper.log("FILE_DOWNLOAD", "📥 Destination path: " + destinationPath);

        // ✅ Ensure the destination directory exists
        File localDir = new File("/home/ntu-user/Downloads/DownloadedFiles/");
        if (!localDir.exists()) {
            LoggerHelper.log("WARNING", "⚠ Destination directory does not exist. Creating it...");
            localDir.mkdirs();
        }

        // ✅ Try downloading the file and log success or failure
        channelSftp.get(containerFilePath, destinationPath);
        LoggerHelper.log("FILE_DOWNLOAD", "✅ Successfully downloaded: " + filename);

        channelSftp.disconnect();
        session.disconnect();
        return true;
    } catch (SftpException e) {
        LoggerHelper.log("ERROR", "❌ SFTP Error: " + e.getMessage());
    } catch (JSchException e) {
        LoggerHelper.log("ERROR", "❌ SSH Connection Error: " + e.getMessage());
    } catch (Exception e) {
        LoggerHelper.log("ERROR", "❌ Unexpected Error: " + e.getMessage());
    }
    return false;
}





   public static List<String> getHealthyContainers() {
    List<String> healthyContainers = new ArrayList<>();

    for (String container : DEST_CONTAINERS) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("ping", "-c", "1", container);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            process.waitFor();

            if (process.exitValue() == 0) {
                healthyContainers.add(container);
            }

        } catch (Exception e) {
            // You can log errors here if needed
        }
    }

    return healthyContainers;  // ✅ Only return containers, don't log repeatedly
}


    

   
    private static void sortByFileSize(List<String> sourceFiles, List<String> destFiles) {
    List<File> files = new ArrayList<>();
    for (String filePath : sourceFiles) {
        files.add(new File(filePath));
    }

    // Sort files by size (ascending order)
    files.sort(Comparator.comparingLong(File::length));

    List<String> sortedSources = new ArrayList<>();
    List<String> sortedDestinations = new ArrayList<>();

    for (File file : files) {
        int index = sourceFiles.indexOf(file.getAbsolutePath());
        sortedSources.add(sourceFiles.get(index));

        // Assign file to a container based on index
        int containerIndex = sortedSources.size() % DEST_CONTAINERS.length;
        sortedDestinations.add("/home/ntu-user/" + DEST_CONTAINERS[containerIndex] + "/" + file.getName());
    }

    sourceFiles.clear();
    sourceFiles.addAll(sortedSources);

    destFiles.clear();
    destFiles.addAll(sortedDestinations);
}

    private static List<String> roundRobinOrdering(List<String> fileList) {
    List<String> reorderedList = new ArrayList<>(fileList);
    int numContainers = DEST_CONTAINERS.length;

    for (int i = 0; i < fileList.size(); i++) {
        reorderedList.set(i, fileList.get(i % numContainers)); // Distribute evenly
    }

    return reorderedList;
}

    




    private static Session connectToHost(JSch jsch, String host) throws JSchException {
        Session session = jsch.getSession(USERNAME, host, REMOTE_PORT);
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.setPassword(PASSWORD);
        session.connect(SESSION_TIMEOUT);
        return session;
    }
    
}
