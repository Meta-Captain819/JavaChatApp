// These are like importing the tools we need to build our chat window
// Think of these as getting our building blocks ready
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

// These help us talk to other people through the internet
// Like setting up telephone lines between computers
import java.io.*;
import java.net.Socket;

// This is the main blueprint for our chat window
public class ClientGUI extends Application {

    // Think of these as different parts of our chat room:
    private TextArea messageArea;    // The big space where all messages appear
    private TextField inputField;    // The box where you type your messages
    private TextField nameField;     // Where you write your nickname
    private PrintWriter writer;      // Our messenger that sends messages to others
    private BufferedReader reader;   // Our messenger that receives messages from others
    private Socket socket;           // Like a phone line connecting us to the chat server
    private String clientName;       // Your nickname in the chat
    private Button reconnectButton;  // A button to rejoin if you get disconnected

    // This is where we build our chat window - like decorating a room
    @Override
    public void start(Stage primaryStage) {
        // Put a nice title at the top of our window
        primaryStage.setTitle("Client Chat");

        // Create a special box for your nickname
        nameField = new TextField();
        nameField.setPromptText("Enter your name and press Enter");
        // When someone types their name and hits Enter:
        nameField.setOnAction(e -> {
            clientName = nameField.getText().trim();  // Save their nickname
            if (!clientName.isEmpty()) {
                connectToServer();              // Try to join the chat room
                nameField.setDisable(true);     // Lock their nickname so it can't change
            }
        });

        // What happens when someone tries to close the chat window
        primaryStage.setOnCloseRequest(event -> {
            cleanup();                // Clean up our connection nicely
            primaryStage.close();     // Close this window
            event.consume();          // Don't close the whole program
        });

        // Create the main chat display area
        messageArea = new TextArea();
        messageArea.setEditable(false);     // People can't edit past messages
        messageArea.setWrapText(true);      // Long messages wrap to the next line

        // Create the box where people type their messages
        inputField = new TextField();
        inputField.setPromptText("Type your message...");
        inputField.setOnAction(e -> sendMessage());  // Send when Enter is pressed
        inputField.setDisable(true);                 // Can't type until connected

        // Create a Send button for sending messages
        Button sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendMessage());
        sendButton.setDisable(true);  // Can't click until connected

        // Create a Reconnect button for when we lose connection
        reconnectButton = new Button("Reconnect");
        reconnectButton.setOnAction(e -> reconnectToServer());
        reconnectButton.setDisable(true);  // Only enabled when disconnected

        // Arrange the typing box and buttons in a nice row
        HBox inputBox = new HBox(10, inputField, sendButton, reconnectButton);
        HBox.setHgrow(inputField, Priority.ALWAYS);  // Let the typing box stretch

        // Stack everything neatly in the window
        VBox root = new VBox(10, nameField, messageArea, inputBox);
        root.setPrefSize(500, 400);  // Make the window a nice size
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Remember which button goes with the input field
        inputField.setUserData(sendButton); // link sendButton for enabling/disabling
    }

    // This helps us reconnect if we get disconnected
    private void reconnectToServer() {
        if (socket != null && !socket.isClosed()) {
            cleanup();  // Close any existing connection first
        }
        connectToServer();  // Try to connect again
    }

    // This is where we actually join the chat server
    private void connectToServer() {
        try {
            // Create our connection to the chat server
            socket = new Socket("localhost", 1234);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Tell everyone who we are
            writer.println(clientName);
            messageArea.appendText("Connected to server as " + clientName + "\n");

            // Start listening for messages from other people
            Thread readThread = new Thread(() -> {
                String line;
                try {
                    // Keep checking for new messages
                    while ((line = reader.readLine()) != null) {
                        String finalLine = line;
                        // Show the message in our chat window
                        Platform.runLater(() -> messageArea.appendText(finalLine + "\n"));
                    }
                    throw new IOException("Server has shut down.");
                } catch (IOException e) {
                    handleDisconnection();  // Handle any connection problems
                }
            });
            readThread.setDaemon(true);  // Close this thread when the program closes
            readThread.start();

            // Enable the chat controls now that we're connected
            inputField.setDisable(false);
            ((Button) inputField.getUserData()).setDisable(false);
            reconnectButton.setDisable(true);
            nameField.setDisable(true);

        } catch (IOException e) {
            showError("Could not connect to server.");
            handleDisconnection();
        }
    }

    // This handles what happens when we lose connection
    private void handleDisconnection() {
        Platform.runLater(() -> {
            showError("Server disconnected.");
            cleanup();
            // Disable chat controls until we reconnect
            inputField.setDisable(true);
            ((Button) inputField.getUserData()).setDisable(true);
            reconnectButton.setDisable(false);
            nameField.setDisable(false);
        });
    }

    // This sends our message to everyone in the chat
    private void sendMessage() {
        String message = inputField.getText();
        if (!message.isEmpty() && writer != null) {
            try {
                writer.println(message);
                if (writer.checkError()) {
                    throw new IOException("Failed to send message");
                }
                // Show our message in our own chat window
                messageArea.appendText("You: " + message + "\n");
                inputField.clear();  // Clear the input box for the next message
            } catch (IOException e) {
                handleDisconnection();
            }
        }
    }

    // This cleans up our connection when we're done
    private void cleanup() {
        try {
            // Close all our communication channels nicely
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

    // This shows error messages in the chat window
    private void showError(String msg) {
        Platform.runLater(() -> messageArea.appendText("[Error] " + msg + "\n"));
    }

    // This starts up our chat program
    public static void main(String[] args) {
        launch(args);
    }
}
