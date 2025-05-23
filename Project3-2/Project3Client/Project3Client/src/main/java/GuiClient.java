import java.util.HashMap;
import javafx.scene.Scene;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.control.TextArea;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.geometry.Pos;
import java.io.Serializable;
import javafx.scene.image.Image;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Locale;

public class GuiClient extends Application {
	private Stage primaryStage;
	private Client client;
	private String username;
	private int playerId;
	private Button[][] buttons = new Button[6][7];

	private Scene loginScene, gameScene, resultScene;
	private TextField usernameField, portField, chatInput;
	private TextArea chatArea;
	private Label statusLabel;
	private int wins, losses, ties = 0;


	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) {
		this.primaryStage = primaryStage;
		setupLoginScene();
		primaryStage.setScene(loginScene);
		primaryStage.setTitle("Connect 4 - Login");
		primaryStage.show();
	}

	private void setupLoginScene() {
		VBox layout = new VBox(10);
		layout.setPadding(new Insets(20));
		layout.setAlignment(Pos.CENTER);

		usernameField = new TextField();
		usernameField.setPromptText("Username");
		Button loginButton = new Button("Login");
		Label loginStatus = new Label();

		loginButton.setOnAction(e -> {
			username = usernameField.getText().trim();
			try {
				client = new Client(this::handleMessage);
				client.connect("127.0.0.1", 5555, username);
				client.start();
				loginStatus.setText("Connecting...");
			} catch (Exception ex) {
				loginStatus.setText("Failed to connect: " + ex.getMessage());
			}
		});

		layout.getChildren().addAll(
				new Label("Connect 4 Login"), usernameField, loginButton, loginStatus
		);
		loginScene = new Scene(layout, 300, 250);
	}

	private void setupGameScene() {
		BorderPane root = new BorderPane();
		GridPane grid = new GridPane();
		grid.setPadding(new Insets(10));
		grid.setHgap(5);
		grid.setVgap(5);

		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 7; col++) {
				Button btn = new Button();
				btn.setMinSize(60, 60);
				int c = col;
				btn.setOnAction(e -> handleMove(c));
				grid.add(btn, col, row);
				buttons[row][col] = btn;
			}
		}


		chatArea = new TextArea();
		chatArea.setEditable(false);
		chatInput = new TextField();
		Button sendBtn = new Button("Send");
		sendBtn.setOnAction(e -> {
			client.sendChatMessage(chatInput.getText());
			chatInput.clear();
		});

		VBox chatBox = new VBox(5, chatArea, new HBox(5, chatInput, sendBtn));
		statusLabel = new Label("Waiting for game to start...");

		root.setCenter(grid);
		root.setRight(chatBox);
		root.setBottom(statusLabel);

		Image backgroundImage = new Image("ocean3.png");
		BackgroundImage bgImage = new BackgroundImage(
				backgroundImage,
				BackgroundRepeat.NO_REPEAT,
				BackgroundRepeat.NO_REPEAT,
				BackgroundPosition.CENTER,
				new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, false, false, true, true)
		);

		root.setBackground(new Background(bgImage));
		gameScene = new Scene(root, 800, 500);
	}
	private void handleMove(int col) {
		for (int row = 5; row >= 0; row--) {
			String style = buttons[row][col].getStyle();
			if (style == null || style.isEmpty()) {
				client.sendMove(row, col);
				break;
			}
		}
	}


	private void handleMessage(Serializable msg) {
		if (msg instanceof Message message) {
			switch (message.getType()) {
				case LOGIN_SUCCESS -> Platform.runLater(() -> {
				});
				case CHAT -> Platform.runLater(() ->
						chatArea.appendText(message.getContent().toString() + "\n")
				);
				case MOVE -> Platform.runLater(() -> {
					int[] move = (int[]) message.getContent();
					int row = move[0], col = move[1], player = move[2];
					String fishImage = (player == 1) ? "redfish.png" : "yellowFish.png";
					buttons[row][col].setStyle("-fx-background-image: url('file:src/main/resources/" + fishImage + "'); " +
							"-fx-background-size: cover;");
				});

				case GAME_START -> Platform.runLater(() -> {
					setupGameScene();
					primaryStage.setScene(gameScene);
					primaryStage.setTitle("Connect 4 Game");
					statusLabel.setText(message.getContent().toString());
				});
				case GAME_END -> Platform.runLater(() -> {
					String resultMessage = message.getContent().toString();

					if (resultMessage.contains("wins")) {
						if (resultMessage.contains(username)) {
							wins++;
						} else {
							losses++;
						}
					} else if (resultMessage.contains("Tie") || resultMessage.contains("tie")) {
						ties++;
					}

					statusLabel.setText(resultMessage);
					resultScreen(resultMessage);
				});


				case LOGIN_FAIL, ERROR -> Platform.runLater(() -> {
					Alert alert = new Alert(Alert.AlertType.ERROR, message.getContent().toString());
					alert.showAndWait();
				});
			}
		}
	}

	private void resultScreen(String message) {
		VBox layout = new VBox(20);
		layout.setAlignment(Pos.CENTER);

		Label resultLabel = new Label(message);
		Label statsLabel = new Label("Wins: " + wins + "\n Losses: " + losses + "\n Ties: " + ties);

		Button restartButton = new Button("Restart");
		Button quitButton = new Button("Quit");

		restartButton.setOnAction(e -> {
			client.send(new Message(MessageType.RESTART, null));
		});

		quitButton.setOnAction(e -> {
			client.disconnect();
			Platform.exit();
		});

		layout.getChildren().addAll(resultLabel, statsLabel, restartButton, quitButton);
		resultScene = new Scene(layout, 800, 500);
		primaryStage.setScene(resultScene);
	}
	private void clearBoard() {
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 7; col++) {
				buttons[row][col].setStyle("");
			}
		}
		chatArea.clear();
	}


}