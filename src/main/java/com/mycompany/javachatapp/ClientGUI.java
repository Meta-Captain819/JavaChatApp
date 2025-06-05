import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;

public class ClientGUI extends Application {

    private TextArea messageArea;
    private TextField inputField;
    private TextField nameField;
    private PrintWriter writer;
    private BufferedReader reader;
    private Socket socket;
    private String clientName;
    private Button reconnectButton;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Client Chat");

        nameField = new TextField();
        nameField.setPromptText("Enter your name and press Enter");
        nameField.setOnAction(e -> {
            clientName = nameField.getText().trim();
            if (!clientName.isEmpty()) {
                connectToServer();
                nameField.setDisable(true);
            }
        });

        // IMPORTANT: Change close behavior to only close this window, NOT the entire app
        primaryStage.setOnCloseRequest(event -> {
            cleanup();
            // Only close this window
            primaryStage.close();
            // Do NOT call Platform.exit() or System.exit(0) here
            event.consume();  // optional to avoid app closing completely if needed
        });

        messageArea = new TextArea();
        messageArea.setEditable(false);
        messageArea.setWrapText(true);

        inputField = new TextField();
        inputField.setPromptText("Type your message...");
        inputField.setOnAction(e -> sendMessage());
        inputField.setDisable(true);

        Button sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendMessage());
        sendButton.setDisable(true);

        reconnectButton = new Button("Reconnect");
        reconnectButton.setOnAction(e -> reconnectToServer());
        reconnectButton.setDisable(true);

        HBox inputBox = new HBox(10, inputField, sendButton, reconnectButton);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        VBox root = new VBox(10, nameField, messageArea, inputBox);
        root.setPrefSize(500, 400);
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();

        inputField.setUserData(sendButton); // link sendButton for enabling/disabling
    }

    private void reconnectToServer() {
        if (socket != null && !socket.isClosed()) {
            cleanup();
        }
        connectToServer();
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 1234);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send name as first message
            writer.println(clientName);
            messageArea.appendText("Connected to server as " + clientName + "\n");

            Thread readThread = new Thread(() -> {
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        String finalLine = line;
                        Platform.runLater(() -> messageArea.appendText(finalLine + "\n"));
                    }
                    throw new IOException("Server has shut down.");
                } catch (IOException e) {
                    handleDisconnection();
                }
            });
            readThread.setDaemon(true);
            readThread.start();

            inputField.setDisable(false);
            ((Button) inputField.getUserData()).setDisable(false);
            reconnectButton.setDisable(true);
            nameField.setDisable(true);

        } catch (IOException e) {
            showError("Could not connect to server.");
            handleDisconnection();
        }
    }

    private void handleDisconnection() {
        Platform.runLater(() -> {
            showError("Server disconnected.");
            cleanup();
            inputField.setDisable(true);
            ((Button) inputField.getUserData()).setDisable(true);
            reconnectButton.setDisable(false);
            nameField.setDisable(false);
        });
    }

    private void sendMessage() {
        String message = inputField.getText();
        if (!message.isEmpty() && writer != null) {
            try {
                writer.println(message);
                if (writer.checkError()) {
                    throw new IOException("Failed to send message");
                }
                messageArea.appendText("You: " + message + "\n");
                inputField.clear();
            } catch (IOException e) {
                handleDisconnection();
            }
        }
    }

    private void cleanup() {
        try {
            if (writer != null) {
                writer.close();
                writer = null;
            }
            if (reader != null) {
                reader.close();
                reader = null;
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        Platform.runLater(() -> messageArea.appendText("[Error] " + msg + "\n"));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
