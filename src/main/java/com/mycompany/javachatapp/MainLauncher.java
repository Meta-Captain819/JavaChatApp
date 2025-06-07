// Import the tools we need to build our launcher window
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

// This is the main starting point of our chat application
// It creates a window with two buttons - one to start the server and one for clients
public class MainLauncher extends Application {

    // These buttons will let users start either the server or a client
    private Button serverButton;    // Button to launch the chat server
    private Button clientButton;    // Button to launch a chat client

    // This method sets up our main launcher window
    @Override
    public void start(Stage primaryStage) {
        // Create two buttons with descriptive labels
        serverButton = new Button("Run Server");
        clientButton = new Button("Run Client");

        // What happens when someone clicks the server button
        serverButton.setOnAction(e -> {
            try {
                // Create a new server window
                ServerGUI serverGUI = new ServerGUI();

                // Prevent users from opening multiple servers at once
                serverButton.setDisable(true);

                // Set up the server's window
                Stage serverStage = new Stage();
                serverGUI.start(serverStage);

                // When someone closes the server window:
                serverStage.setOnCloseRequest(event -> {
                    try {
                        serverGUI.stop(); // Clean up server connections properly
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    // Allow users to start another server if they want
                    serverButton.setDisable(false);
                });

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // What happens when someone clicks the client button
        clientButton.setOnAction(e -> {
            try {
                // Create and show a new client chat window
                ClientGUI clientGUI = new ClientGUI();
                Stage clientStage = new Stage();
                clientGUI.start(clientStage);

                // Users can open multiple client windows, so we don't
                // need to disable the button here
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // Arrange the buttons vertically in the center of the window
        VBox root = new VBox(20, serverButton, clientButton);  // 20 pixels space between buttons
        root.setAlignment(Pos.CENTER);

        // Create and show the main launcher window
        Scene scene = new Scene(root, 300, 200);  // Window size: 300x200 pixels
        primaryStage.setTitle("Java Chat App Launcher");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // This is the entry point of our application
    public static void main(String[] args) {
        launch(args);
    }
}
