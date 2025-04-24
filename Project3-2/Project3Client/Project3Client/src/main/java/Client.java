import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.function.Consumer;

import javafx.application.Application;

public class Client extends Thread {
	private Socket socket;
	private ObjectOutputStream out;
	private ObjectInputStream in;
	private Consumer<Serializable> callback;
	private String username;
	private int playerId;

	public Client(Consumer<Serializable> callback) {
		this.callback = callback;
	}

	public void connect(String ip, int port, String username) throws IOException {
		this.socket = new Socket(ip, port);
		this.out = new ObjectOutputStream(socket.getOutputStream());
		this.in = new ObjectInputStream(socket.getInputStream());
		this.username = username;
		send(new Message(MessageType.LOGIN, username));
	}

	public void run() {
		try {
			while (true) {
				Serializable data = (Serializable) in.readObject();
				callback.accept(data);
			}
		} catch (Exception e) {
			callback.accept(new Message(MessageType.ERROR, "Connection lost"));
		}
	}

	public void send(Serializable data) {
		try {
			out.writeObject(data);
		} catch (IOException e) {
			callback.accept(new Message(MessageType.ERROR, "Failed to send data"));
		}
	}

	public void sendChatMessage(String message) {
		send(new Message(MessageType.CHAT, message));
	}

	public void sendMove(int row, int col) {
		send(new Message(MessageType.MOVE, new int[]{row, col}));
	}

	public void setPlayerId(int playerId) {
		this.playerId = playerId;
	}

	public int getPlayerId() {
		return playerId;
	}

	public String getUsername() {
		return username;
	}

	public void disconnect() {
		try {
			socket.close();
		} catch (IOException e) {}
	}
}