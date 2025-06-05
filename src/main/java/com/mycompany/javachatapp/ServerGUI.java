import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.*;

public class ServerGUI extends Application {

    private TextArea messageArea;
    private TextField inputField;
    private ComboBox<String> clientSelector;
    private Button sendButton;

    private ServerSocket serverSocket;
    private ExecutorService pool = Executors.newCachedThreadPool();
    private Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    @Override
    public void start(Stage primaryStage) {
        messageArea = new TextArea();
        messageArea.setEditable(false);

        inputField = new TextField();
        inputField.setPromptText("Type your message...");

        clientSelector = new ComboBox<>();
        clientSelector.getItems().add("All");
        clientSelector.setValue("All");

        sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendMessage());

        HBox inputBox = new HBox(10, new Label("To:"), clientSelector, inputField, sendButton);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        VBox root = new VBox(10, messageArea, inputBox);
        root.setPrefSize(600, 400);

        primaryStage.setTitle("Server Chat");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> {
            try {
                stop(); // Clean up resources properly
                Platform.exit();
                System.exit(0);
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        });

        // Start server in a background thread
        new Thread(this::startServer).start();
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(1234);
            Platform.runLater(() -> appendMessage("Server started. Waiting for clients..."));

            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                pool.execute(handler);
            }
        } catch (IOException e) {
            if (serverSocket == null || serverSocket.isClosed()) {
                Platform.runLater(() -> appendMessage("Server stopped."));
            } else {
                Platform.runLater(() -> appendMessage("Server error: " + e.getMessage()));
            }
        }
    }

    private void sendMessage() {
        String target = clientSelector.getValue();
        String msg = inputField.getText().trim();
        if (msg.isEmpty()) return;

        appendMessage("Server to " + target + ": " + msg);

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
        inputField.clear();
    }

    private void appendMessage(String msg) {
        Platform.runLater(() -> messageArea.appendText(msg + "\n"));
    }

    @Override
    public void stop() throws Exception {
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

        // Shutdown the thread pool
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                clientName = in.readLine();

                if (clientName == null || clientName.trim().isEmpty()) {
                    out.println("Error: Invalid client name.");
                    socket.close();
                    return;
                }

                // Reject duplicate client names
                if (clients.putIfAbsent(clientName, this) != null) {
                    out.println("Error: Name already taken. Disconnecting.");
                    socket.close();
                    return;
                }

                Platform.runLater(() -> {
                    clientSelector.getItems().add(clientName);
                    appendMessage(clientName + " connected.");
                });

                String msg;
                while ((msg = in.readLine()) != null) {
                    String received = clientName + ": " + msg;
                    Platform.runLater(() -> appendMessage(received));
                }
                throw new IOException("Client " + clientName + " disconnected.");

            } catch (IOException e) {
                Platform.runLater(() -> appendMessage("Connection with " + clientName + " lost."));
            } finally {
                clients.remove(clientName);
                Platform.runLater(() -> clientSelector.getItems().remove(clientName));
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException ignored) {}
            }
        }

        public void send(String msg) {
            if (out != null) {
                out.println(msg);
            }
        }

        public void closeConnection() throws IOException {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
