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
	private final Map<ClientThread, Boolean> restartVotes = new HashMap<>();


	private final Consumer<Message> callback;
	private final Queue<ClientThread> waitingClients = new LinkedList<>();
	private final List<GameSession> activeGames = new ArrayList<>();
	private int currentPlayerTurn = 1;

	private int count = -1;



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
					callback.accept(new Message(MessageType.NEWUSER, username,count));
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
					if (message.getType() == MessageType.MOVE || message.getType() == MessageType.CHAT || message.getType() == MessageType.RESTART) {
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
					callback.accept(new Message(MessageType.DISCONNECT, username + " disconnected",count));
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
		private final int[][] board = new int[6][7];
		private final ClientThread player1;
		private final ClientThread player2;
		private boolean player1WantsRestart = false;
		private boolean player2WantsRestart = false;


		public GameSession(ClientThread p1, ClientThread p2) {
			this.player1 = p1;
			this.player2 = p2;
			restartVotes.put(p1, false);
			restartVotes.put(p2, false);
		}
		public void resetBoard() {
			for (int row = 0; row < 6; row++) {
				for (int col = 0; col < 7; col++) {
					board[row][col] = 0;
				}
			}
			currentPlayerTurn = 1;
		}
		public void sendToBoth(Message message) {
			player1.send(message);
			player2.send(message);
		}

		public void sendToOpponent(ClientThread sender, Message message) {
			if (message.getType() == MessageType.MOVE) {
				int playerNumber = (sender == player1) ? 1 : 2;

				if (playerNumber != currentPlayerTurn) {
					sender.send(new Message(MessageType.ERROR, "It's not your turn!"));
					return;
				}

				if (message.getType() == MessageType.MOVE) {
					 playerNumber = (sender == player1) ? 1 : 2;
					if (playerNumber != currentPlayerTurn) {
						sender.send(new Message(MessageType.ERROR, "It's not your turn!"));
						return;
					}

					int[] move = (int[]) message.getContent();
					int row = move[0], col = move[1];
					if (board[row][col] != 0) return;
					board[row][col] = playerNumber;

					int[] moveWithPlayer = new int[]{row, col, playerNumber};
					player1.send(new Message(MessageType.MOVE, moveWithPlayer));
					player2.send(new Message(MessageType.MOVE, moveWithPlayer));

					if (checkWin(row, col, playerNumber)) {
						String winnerMsg =  sender.username + ": wins!";
						sendToBoth(new Message(MessageType.GAME_END, winnerMsg));
						callback.accept(new Message(MessageType.CHAT, winnerMsg,count));
					} else if (checkDraw()) {
						sendToBoth(new Message(MessageType.GAME_END, "Its a Tie!"));
						callback.accept(new Message(MessageType.CHAT, "Its a Tie!",count));

					} else {
						currentPlayerTurn = (currentPlayerTurn == 1) ? 2 : 1;
					}
				}

			}
			else if (message.getType() == MessageType.CHAT) {
				callback.accept(new Message(MessageType.CHAT, "Chat message from " + sender.username,count));
				Message newMsg = new Message(MessageType.CHAT, sender.username + ": "+ message.getContent());
				player1.send(newMsg);
				player2.send(newMsg);
			}
			else if (message.getType() == MessageType.RESTART) {
				if (sender == player1) {
					player1WantsRestart = true;
				} else if (sender == player2) {
					player2WantsRestart = true;
				}

				if (player1WantsRestart && player2WantsRestart) {
					resetBoard();
					sendToBoth(new Message(MessageType.GAME_START, "Game restarted!"));
					player1WantsRestart = false;
					player2WantsRestart = false;
				} else {
					sender.send(new Message(MessageType.CHAT, "Waiting for your opponent to restart..."));
				}
			}
			else {
				getOpponent(sender).send(message);
			}
		}

		private boolean checkWin(int row, int col, int player) {
			int[][] directions = {{1,0},{0,1},{1,1},{1,-1}};
			for (int[] d : directions) {
				int count = 1;
				count += countDirection(row, col, d[0], d[1], player);
				count += countDirection(row, col, -d[0], -d[1], player);
				if (count >= 4) return true;
			}
			return false;
		}
		private boolean checkDraw() {
			for (int i = 0; i < board.length; i++) {
				for (int j = 0; j < board[0].length; j++) {
					if (board[i][j] == 0)
						return false;
				}
			}
			return true;
		}
	private int countDirection(int row, int col, int dRow, int dCol, int player) {
		int r = row + dRow, c = col + dCol, count = 0;
		while (r >= 0 && r < 6 && c >= 0 && c < 7 && board[r][c] == player) {
			count++;
			r += dRow;
			c += dCol;
		}
		return count;
	}

		public ClientThread getOpponent(ClientThread player) {
			return (player == player1) ? player2 : player1;
		}

		public boolean contains(ClientThread player) {
			return player == player1 || player == player2;
		}


	}

}
