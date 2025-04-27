import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class GuiServer extends Application {

	Server serverConnection;
	ListView<String> listItems;
	ListView<String> listUsers;

	@Override
	public void start(Stage primaryStage) {
		listItems = new ListView<>();
		listUsers = new ListView<>();
		HBox lists = new HBox(listUsers, listItems);

		serverConnection = new Server(data -> Platform.runLater(() -> {
			String user = String.valueOf(data.getContent());
			switch (data.getType()) {
				case CHAT, GAME_START, GAME_END, LOGIN_FAIL, LOGIN_SUCCESS, ERROR:
					listItems.getItems().add(data.getContent().toString());
					break;
				case MOVE:
					listItems.getItems().add(data.getContent().toString());
				case NEWUSER:

					listUsers.getItems().add(String.valueOf(data.getRecipient()+1));
					listItems.getItems().add(user + " joined");

					break;
				case DISCONNECT:
					listUsers.getItems().remove(String.valueOf(data.getRecipient()));
					listItems.getItems().add(user + " has disconnected!\n");
					break;
			}
		}));

		BorderPane root = new BorderPane();
		root.setPadding(new Insets(70));
		root.setStyle("-fx-background-color: coral; -fx-font-family: 'serif';");
		root.setCenter(lists);

		primaryStage.setScene(new Scene(root, 500, 400));
		primaryStage.setTitle("Connect 4 Server");
		primaryStage.setOnCloseRequest(new EventHandler<>() {
			@Override
			public void handle(WindowEvent t) {
				Platform.exit();
				System.exit(0);
			}
		});
		primaryStage.show();
	}

	public static void main(String[] args) {
		launch(args);
	}
}
