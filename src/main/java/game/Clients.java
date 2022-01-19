package game;

import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;
import org.jspace.SequentialSpace;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Clients {}

class Bob {
    public static void main(String[] args) throws IOException, InterruptedException {
        RemoteSpace gameSpace = new RemoteSpace("tcp://localhost:31415/gameId?keep");
        RemoteSpace debug = new RemoteSpace("tcp://localhost:31415/debug?keep");
        SequentialSpace systemSpace = new SequentialSpace();  // To coordinate threads access to system out

        // Only one thread at a time should have access to system out
        systemSpace.put("lock");

        // Start client
        new Thread(new StartClient(gameSpace, debug, systemSpace, "Bob")).start();
    }
}

class Alice {
    public static void main(String[] args) throws IOException, InterruptedException {
        RemoteSpace gameSpace = new RemoteSpace("tcp://localhost:31415/gameId?keep");
        RemoteSpace debug = new RemoteSpace("tcp://localhost:31415/debug?keep");
        SequentialSpace systemSpace = new SequentialSpace();  // To coordinate threads access to system out

        // Only one thread at a time should have access to system out
        systemSpace.put("lock");

        // Start client
        new Thread(new StartClient(gameSpace, debug, systemSpace, "Alice")).start();
    }
}

class StartClient implements Runnable{

    RemoteSpace gameSpace;
    RemoteSpace debugSpace;
    SequentialSpace systemSpace;
    String playerId;
    Scanner scanner = new Scanner(System.in);

    public StartClient(RemoteSpace gameSpace, RemoteSpace debugSpace, SequentialSpace systemSpace, String playerId) {
        this.gameSpace = gameSpace;
        this.debugSpace = debugSpace;
        this.systemSpace = systemSpace;
        this.playerId = playerId;
    }

    @Override
    public void run() {
        try {
            // Print player IDs
            String[] playerIds = (String[]) gameSpace.get(
                    new ActualField(playerId),
                    new ActualField("players"),
                    new FormalField(String[].class)
            )[2];

            systemSpace.get(new ActualField("lock"));
            System.out.println("\nPlayer IDs:");
            for (int i = 0; i < playerIds.length; i++) {
                System.out.println("- " + playerIds[i]);
            }
            systemSpace.put("lock");

            TimeUnit.SECONDS.sleep(1);

            // Threads to update TUI
            new Thread(new ClientBoard(playerId, gameSpace, systemSpace)).start();
            new Thread(new ClientHand(playerId, gameSpace, systemSpace)).start();
            new Thread(new TurnWatcher(playerId, gameSpace, systemSpace)).start();

            TimeUnit.SECONDS.sleep(1);

            while (true) takeTurn();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void takeTurn() throws InterruptedException {

        // Wait for my turn
        gameSpace.get(
                new ActualField(playerId),
                new ActualField("take")
        );

        systemSpace.get(new ActualField("lock"));
        System.out.println("\nEnter to begin");
        systemSpace.put("lock");
        scanner.nextLine();

        // Take turn
        gameSpace.put(playerId, "taken");

        // Do an action: Place or draw card
        doAction();

        // End turn
        gameSpace.put(playerId, "ended");
    }

    private void doAction() throws InterruptedException {

        // Loop until servers approves action
        while (true) {
            Card cardToPlay = null;

            String command = scanner.nextLine();

            // Interpret command
            boolean draw = command.equals("Draw");

            // Branch on command
            if (draw)
                gameSpace.put(
                        playerId,
                        "action",
                        new Action(Actions.DRAW, null)
                );
            else {
                cardToPlay = new Card(
                        command.split(" ")[0],
                        command.split(" ")[1]
                );

                gameSpace.put(
                        playerId,
                        "action",
                        new Action(Actions.PLAY, cardToPlay)
                );
            }

            // Listen for response
            String response = (String) gameSpace.get(
                    new ActualField(playerId),
                    new FormalField(String.class)
            )[1];

            // Branch on response
            if(response.equals("success")) break;
            else {
                systemSpace.get(new ActualField("lock"));
                System.out.println("\nINVALID\n");
                systemSpace.put("lock");
            }
        }
    }
}

// Listen for changes to the board and apply them
class ClientBoard implements Runnable {

    String playerId;
    RemoteSpace gameSpace;
    SequentialSpace systemSpace;

    public ClientBoard(String playerId, RemoteSpace gameSpace, SequentialSpace systemSpace) {
        this.playerId = playerId;
        this.gameSpace = gameSpace;
        this.systemSpace = systemSpace;
    }

    @Override
    public void run() {
        try {
            while (true) {

                // Wait for signal
                Board board = (Board) gameSpace.get(
                        new ActualField(playerId),
                        new ActualField("board"),
                        new FormalField(Board.class)
                )[2];

                // TUI
                systemSpace.get(new ActualField("lock"));
                printBoard(board);
                System.out.flush();
                systemSpace.put("lock");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void printBoard(Board board) throws InterruptedException {
        System.out.println("\n** BOARD **");
        for (Map.Entry<String, Integer> entry : board.getHands().entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

        System.out.println("Top Card: " + board.topCard.color + " " + board.topCard.getValue());
    }
}

// Listen for changes to the player's hand and apply them
class ClientHand implements Runnable {

    String playerId;
    RemoteSpace gameSpace;
    SequentialSpace systemSpace;

    public ClientHand(String playerId, RemoteSpace gameSpace, SequentialSpace systemSpace) {
        this.playerId = playerId;
        this.gameSpace = gameSpace;
        this.systemSpace = systemSpace;
    }

    @Override
    public void run() {
        try {
           while (true) {

               // Wait for signal
               Card[] hand = (Card[]) gameSpace.get(
                       new ActualField(playerId),
                       new ActualField("cards"),
                       new FormalField(Card[].class)
               )[2];

               // TUI
               systemSpace.get(new ActualField("lock"));
               printHand(hand);
               System.out.flush();
               systemSpace.put("lock");
           }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void printHand(Card[] hand) {
        System.out.println("\n** HAND **");
        for (int i = 0; i < hand.length; i++) {
            Card card = hand[i];
            System.out.println(card.getColor() + " " + card.getValue());
        }
    }
}

// Listen for who takes turn and display it
class TurnWatcher implements Runnable {

    String playerId;
    RemoteSpace gameSpace;
    SequentialSpace systemSpace;

    public TurnWatcher(String playerId, RemoteSpace gameSpace, SequentialSpace systemSpace) {
        this.playerId = playerId;
        this.gameSpace = gameSpace;
        this.systemSpace = systemSpace;
    }

    @Override
    public void run() {
        try {
            while (true) {
                String player = (String) gameSpace.get(
                        new ActualField(playerId),
                        new FormalField(String.class),
                        new ActualField("takes")
                )[1];

                System.out.print("");

                // Only print for other players
                if (!player.equals(playerId)) {

                    // TUI
                    systemSpace.get(new ActualField("lock"));
                    System.out.println("\n" + player + " is taking turn");
                    System.out.flush();
                    systemSpace.put("lock");
                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
