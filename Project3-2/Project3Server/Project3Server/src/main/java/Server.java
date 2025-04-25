import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.*;

import javafx.application.Platform;
import javafx.scene.control.ListView;

public class Server {
	private final Map<String, ClientThread> clients = new HashMap<>();
	private final Consumer<Message> callback;
	private final Queue<ClientThread> waitingClients = new LinkedList<>();
	private final List<GameSession> activeGames = new ArrayList<>();

	private int count = 0;

	public Server(Consumer<Message> callback) {
		this.callback = callback;
		new TheServer().start();
	}

	private class TheServer extends Thread {
		public void run() {
			try (ServerSocket serverSocket = new ServerSocket(5555)) {
				System.out.println("Server started on port 5555");

				while (true) {
					Socket socket = serverSocket.accept();
					new ClientThread(socket).start();
				}
			} catch (Exception e) {
				callback.accept(new Message(MessageType.ERROR, "Server failed to start"));
			}
		}
	}

	private class ClientThread extends Thread {
		private final Socket socket;
		private ObjectInputStream in;
		private ObjectOutputStream out;
		private String username;
		private GameSession gameSession;


		public ClientThread(Socket socket) {
			this.socket = socket;
		}
		public void setGameSession(GameSession session) {
			this.gameSession = session;
		}
		public void run() {
			try {
				out = new ObjectOutputStream(socket.getOutputStream());
				in = new ObjectInputStream(socket.getInputStream());

				Message loginMessage = (Message) in.readObject();
				if (loginMessage.getType() != MessageType.LOGIN) {
					send(new Message(MessageType.LOGIN_FAIL, "Invalid login message."));
					return;
				}

				username = loginMessage.getContent().toString().trim();

				synchronized (clients) {
					if (username.isEmpty() || clients.containsKey(username)) {
						send(new Message(MessageType.LOGIN_FAIL, "Username already taken or invalid."));
						return;
					}
					clients.put(username, this);
					count++;
					send(new Message(MessageType.LOGIN_SUCCESS, username));
					callback.accept(new Message(MessageType.NEWUSER, username));
				}
				synchronized (waitingClients) {
					waitingClients.offer(this);
					if (waitingClients.size() >= 2) {
						ClientThread p1 = waitingClients.poll();
						ClientThread p2 = waitingClients.poll();
						GameSession session = new GameSession(p1, p2);
						activeGames.add(session);
						p1.setGameSession(session);
						p2.setGameSession(session);
						session.sendToBoth(new Message(MessageType.GAME_START, "Game started!"));
					} else {
						send(new Message(MessageType.LOGIN_SUCCESS, "Waiting for an opponent..."));
					}
				}


				while (true) {
					Message message = (Message) in.readObject();
					if (message.getType() == MessageType.MOVE || message.getType() == MessageType.CHAT) {
						if (gameSession != null) {
							gameSession.sendToOpponent(this, message);
						}
					}

				}
			} catch (Exception e) {
				disconnect();
			}
		}

		public void send(Message msg) {
			try {
				out.writeObject(msg);
			} catch (Exception e) {
				disconnect();
			}
		}

		public void disconnect() {
			try {
				if (username != null) {
					clients.remove(username);
					count--;
					callback.accept(new Message(MessageType.DISCONNECT, username + " disconnected"));
					broadcast(new Message(MessageType.DISCONNECT, username + " disconnected. Total clients: " + count));
				}
				socket.close();
			} catch (Exception e) {}
		}

		public void broadcast(Message message) {
			for (ClientThread client : clients.values()) {
				client.send(message);
			}
		}
	}

	private class GameSession {
		private final ClientThread player1;
		private final ClientThread player2;

		public GameSession(ClientThread p1, ClientThread p2) {
			this.player1 = p1;
			this.player2 = p2;
		}

		public void sendToBoth(Message message) {
			player1.send(message);
			player2.send(message);
		}

		public void sendToOpponent(ClientThread sender, Message message) {
			int playerNumber = (sender == player1) ? 1 : 2;

			if (message.getType() == MessageType.MOVE) {
				int[] move = (int[]) message.getContent();
				int[] moveWithPlayer = new int[]{move[0], move[1], playerNumber};
				Message newMsg = new Message(MessageType.MOVE, moveWithPlayer);
				getOpponent(sender).send(newMsg);
				sender.send(newMsg);
			} else {
				getOpponent(sender).send(message);
			}
		}

		public ClientThread getOpponent(ClientThread player) {
			return (player == player1) ? player2 : player1;
		}

		public boolean contains(ClientThread player) {
			return player == player1 || player == player2;
		}
	}

}
