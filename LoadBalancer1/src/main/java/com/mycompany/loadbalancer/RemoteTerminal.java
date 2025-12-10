package com.mycompany.loadbalancer;

import com.jcraft.jsch.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;

public class RemoteTerminal {

    @FXML
    private ChoiceBox<String> containerChoiceBox;
    @FXML
    private TextArea terminalOutput;
    @FXML
    private TextField commandInput;
    @FXML
    private Button executeButton;

    private static final String USERNAME = "ntu-user";
    private static final String PASSWORD = "ntu-user";
    private static final int SSH_PORT = 22;

    @FXML
    public void initialize() {
        containerChoiceBox.getItems().addAll(App.getHealthyContainers());
    }

    @FXML
    private void handleExecuteCommand() {
        String selectedContainer = containerChoiceBox.getValue();
        String command = commandInput.getText().trim();

        if (selectedContainer == null || selectedContainer.isEmpty()) {
            terminalOutput.appendText("❌ Please select a container.\n");
            return;
        }

        if (command.isEmpty()) {
            terminalOutput.appendText("❌ Please enter a command.\n");
            return;
        }

        executeRemoteCommand(selectedContainer, command);
    }

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

                Channel channel = session.openChannel("exec");
                ((ChannelExec) channel).setCommand(command);
                channel.setInputStream(null);
                ((ChannelExec) channel).setErrStream(System.err);

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
                    terminalOutput.appendText("📡 Command Executed on " + container + ":\n" + output + "\n");
                });

                LoggerHelper.log("SSH_SESSION", "Executed command on " + container + ": " + command);
            } catch (Exception e) {
                Platform.runLater(() -> terminalOutput.appendText("❌ Error executing command: " + e.getMessage() + "\n"));
                LoggerHelper.log("ERROR", "Failed to execute command on " + container + ": " + e.getMessage());
            }
        }).start();
    }
}
