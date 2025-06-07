// Import the tools we need to build our server window and handle network communication
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

// These help us manage network connections and multiple clients
import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.*;

// This is the main blueprint for our chat server window
public class ServerGUI extends Application {

    // These are the visual parts of our server window
    private TextArea messageArea;        // Shows all chat messages and server updates
    private TextField inputField;        // Where the server admin types messages
    private ComboBox<String> clientSelector;  // Dropdown to select which client to message
    private Button sendButton;           // Button to send server messages

    // These handle the behind-the-scenes server operations
    private ServerSocket serverSocket;   // The main door where clients connect
    private ExecutorService pool;        // Manages all the client connections efficiently
    private Map<String, ClientHandler> clients;  // Keeps track of all connected clients

    // This sets up our server window - like arranging furniture in a room
    @Override
    public void start(Stage primaryStage) {
        // Create the message display area
        messageArea = new TextArea();
        messageArea.setEditable(false);  // Only for displaying, not editing

        // Create the message input field
        inputField = new TextField();
        inputField.setPromptText("Type your message...");

        // Create the client selector dropdown
        clientSelector = new ComboBox<>();
        clientSelector.getItems().add("All");  // Option to message everyone
        clientSelector.setValue("All");        // Default to messaging everyone

        // Create the send button
        sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendMessage());  // What happens when clicked

        // Arrange the input controls in a horizontal row
        HBox inputBox = new HBox(10, new Label("To:"), clientSelector, inputField, sendButton);
        HBox.setHgrow(inputField, Priority.ALWAYS);  // Let the input field stretch

        // Stack everything vertically in the window
        VBox root = new VBox(10, messageArea, inputBox);
        root.setPrefSize(600, 400);  // Set a nice window size

        // Set up the main window
        primaryStage.setTitle("Server Chat");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();

        // Handle when someone tries to close the server window
        primaryStage.setOnCloseRequest(event -> {
            try {
                stop();  // Clean up all connections
                Platform.exit();
                System.exit(0);
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        });

        // Start the server in the background
        new Thread(this::startServer).start();
    }

    // This is where the actual server starts running in the background
    private void startServer() {
        try {
            // Open a door (port 1234) for clients to connect
            serverSocket = new ServerSocket(1234);
            // Let everyone know we're ready for clients
            Platform.runLater(() -> appendMessage("Server started. Waiting for clients..."));

            // Keep watching for new clients until the server is closed
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();  // Wait for a client to knock
                ClientHandler handler = new ClientHandler(socket);  // Create a helper for this client
                pool.execute(handler);  // Hand off client handling to our worker pool
            }
        } catch (IOException e) {
            // Handle server shutdown gracefully
            if (serverSocket == null || serverSocket.isClosed()) {
                Platform.runLater(() -> appendMessage("Server stopped."));
            } else {
                Platform.runLater(() -> appendMessage("Server error: " + e.getMessage()));
            }
        }
    }

    // Handles sending messages from the server to clients
    private void sendMessage() {
        String target = clientSelector.getValue();  // Who should get the message
        String msg = inputField.getText().trim();   // What's the message
        if (msg.isEmpty()) return;  // Don't send empty messages

        appendMessage("Server to " + target + ": " + msg);  // Show in our window

        // Either send to everyone or just one person
        if ("All".equals(target)) {
            clients.values().forEach(client -> client.send("Server (broadcast): " + msg));
        } else {
            ClientHandler client = clients.get(target);
            if (client != null) {
                client.send("Server: " + msg);
            } else {
                appendMessage("[Error] Client " + target + " not found.");
            }
        }
        inputField.clear();  // Clear the input field after sending
    }

    // Safely adds messages to our display area from any thread
    private void appendMessage(String msg) {
        Platform.runLater(() -> messageArea.appendText(msg + "\n"));
    }

    // Clean up everything when the server shuts down
    @Override
    public void stop() throws Exception {
        // Close the main server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        // Close all client connections
        for (ClientHandler client : clients.values()) {
            try {
                client.closeConnection();
            } catch (IOException e) {
                System.err.println("Error closing client connection: " + e.getMessage());
            }
        }
        clients.clear();

        // Shut down our worker pool gracefully
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();  // Force shutdown if taking too long
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // This is like a personal assistant for each connected client
    private class ClientHandler implements Runnable {
        private Socket socket;          // The client's connection
        private BufferedReader in;      // Read messages from client
        private PrintWriter out;        // Send messages to client
        private String clientName;      // Client's chosen name

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // Set up ways to talk to the client
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // First message from client should be their name
                clientName = in.readLine();

                // Make sure they sent a valid name
                if (clientName == null || clientName.trim().isEmpty()) {
                    out.println("Error: Invalid client name.");
                    socket.close();
                    return;
                }

                // Don't allow duplicate names
                if (clients.putIfAbsent(clientName, this) != null) {
                    out.println("Error: Name already taken. Disconnecting.");
                    socket.close();
                    return;
                }

                // Update the GUI to show new client
                Platform.runLater(() -> {
                    clientSelector.getItems().add(clientName);
                    appendMessage(clientName + " connected.");
                });

                // Main loop: keep listening for client messages
                String msg;
                while ((msg = in.readLine()) != null) {
                    String received = clientName + ": " + msg;
                    Platform.runLater(() -> appendMessage(received));
                }

            } catch (IOException e) {
                Platform.runLater(() -> appendMessage("Connection with " + clientName + " lost."));
            } finally {
                // Clean up when client disconnects
                clients.remove(clientName);
                Platform.runLater(() -> clientSelector.getItems().remove(clientName));
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException ignored) {}
            }
        }

        // Send a message to this client
        public void send(String msg) {
            if (out != null) {
                out.println(msg);
            }
        }

        // Close this client's connection
        public void closeConnection() throws IOException {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    // Start up our server application
    public static void main(String[] args) {
        launch(args);
    }
}
