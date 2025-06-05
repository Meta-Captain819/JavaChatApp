import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainLauncher extends Application {

    private Button serverButton;
    private Button clientButton;

    @Override
    public void start(Stage primaryStage) {
        serverButton = new Button("Run Server");
        clientButton = new Button("Run Client");

        serverButton.setOnAction(e -> {
            try {
                ServerGUI serverGUI = new ServerGUI();

                // Disable the server button when server window opens
                serverButton.setDisable(true);

                Stage serverStage = new Stage();
                serverGUI.start(serverStage);

                // When the server window closes, enable the button again
                serverStage.setOnCloseRequest(event -> {
                    try {
                        serverGUI.stop(); // cleanup server resources
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    serverButton.setDisable(false);
                });

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        clientButton.setOnAction(e -> {
            try {
                ClientGUI clientGUI = new ClientGUI();
                Stage clientStage = new Stage();
                clientGUI.start(clientStage);

                // Optional: you can add logic to disable clientButton on open
                // and enable on close similarly if you want
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        VBox root = new VBox(20, serverButton, clientButton);
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, 300, 200);
        primaryStage.setTitle("Java Chat App Launcher");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
