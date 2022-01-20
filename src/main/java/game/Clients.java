package game;

import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;
import org.jspace.SequentialSpace;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Clients {
}

class Mark {
    public static void main(String[] args) throws IOException, InterruptedException {
        RemoteSpace gameSpace = new RemoteSpace("tcp://localhost:31415/gameId?keep");
        RemoteSpace debug = new RemoteSpace("tcp://localhost:31415/debug?keep");
        SequentialSpace systemSpace = new SequentialSpace();  // To coordinate threads access to system out

        // Only one thread at a time should have access to system out
        systemSpace.put("lock");

        // Start client
        new Thread(new StartClient(gameSpace, debug, systemSpace, "Mark")).start();
    }
}

class Talha {
    public static void main(String[] args) throws IOException, InterruptedException {
        RemoteSpace gameSpace = new RemoteSpace("tcp://localhost:31415/gameId?keep");
        RemoteSpace debug = new RemoteSpace("tcp://localhost:31415/debug?keep");
        SequentialSpace systemSpace = new SequentialSpace();  // To coordinate threads access to system out

        // Only one thread at a time should have access to system out
        systemSpace.put("lock");

        // Start client
        new Thread(new StartClient(gameSpace, debug, systemSpace, "Talha")).start();
    }
}

class Volkan {
    public static void main(String[] args) throws IOException, InterruptedException {
        RemoteSpace gameSpace = new RemoteSpace("tcp://localhost:31415/gameId?keep");
        RemoteSpace debug = new RemoteSpace("tcp://localhost:31415/debug?keep");
        SequentialSpace systemSpace = new SequentialSpace();  // To coordinate threads access to system out

        // Only one thread at a time should have access to system out
        systemSpace.put("lock");

        // Start client
        new Thread(new StartClient(gameSpace, debug, systemSpace, "Volkan")).start();
    }
}

class Mikkel {
    public static void main(String[] args) throws IOException, InterruptedException {
        RemoteSpace gameSpace = new RemoteSpace("tcp://localhost:31415/gameId?keep");
        RemoteSpace debug = new RemoteSpace("tcp://localhost:31415/debug?keep");
        SequentialSpace systemSpace = new SequentialSpace();  // To coordinate threads access to system out

        // Only one thread at a time should have access to system out
        systemSpace.put("lock");

        // Start client
        new Thread(new StartClient(gameSpace, debug, systemSpace, "Mikkel")).start();
    }
}

class UNO {
    public static void main(String[] args) throws IOException, InterruptedException {
        RemoteSpace gameSpace = new RemoteSpace("tcp://localhost:31415/gameId?keep");
        new startUNO(gameSpace);
    }
}

class startUNO {

    RemoteSpace gameSpace;
    Scanner scanner = new Scanner(System.in);

    public startUNO(RemoteSpace gameSpace) throws InterruptedException {

        this.gameSpace = gameSpace;
       while (true) {

           // Commands:
           // - playerId "UNO"
           // - playerId "missingUNO"
           String command = scanner.nextLine();
           System.out.println(command);
           gameSpace.put(command.split(" ")[0], command.split(" ")[1]);
       }
    }
}

class StartClient implements Runnable {

    RemoteSpace gameSpace;
    RemoteSpace debugSpace;
    SequentialSpace systemSpace;
    String playerId;
    boolean gameDone = false;
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
            new Thread(new ClientBoard(playerId, gameSpace, systemSpace, this)).start();
            new Thread(new ClientHand(playerId, gameSpace, systemSpace, this)).start();
            new Thread(new TurnWatcher(playerId, gameSpace, systemSpace, this)).start();
            new Thread(new ListenUNO(playerId, gameSpace, systemSpace, this)).start();
            new Thread(new ListenMissingUNO(playerId, gameSpace, systemSpace, this)).start();

            TimeUnit.SECONDS.sleep(1);

            // Notify server the thread is ready
            gameSpace.put(playerId, "ready");

            // Wait until all players are ready
            gameSpace.get(
                    new ActualField(playerId),
                    new ActualField("allReady")
            );

            systemSpace.get(new ActualField("lock"));
            System.out.println("\nAll players are ready");
            systemSpace.put("lock");

            // Play the game until a winner is found
            while (true)
                if (!takeTurn()) break;

            gameDone = true;

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Return true if game is still going
    private boolean takeTurn() throws InterruptedException {

        // Wait for my turn
        String status = (String) gameSpace.get(
                new ActualField(playerId),
                new ActualField("take"),
                new FormalField(String.class)
        )[2];

        //Check if game is done
        if (!status.equals("alive")) {
            systemSpace.get(new ActualField("lock"));
            System.out.println("\nThe Winner is: " + status + "!");
            systemSpace.put("lock");
            return false;
        }



        systemSpace.get(new ActualField("lock"));
        System.out.println("\nEnter to begin");
        systemSpace.put("lock");
        scanner.nextLine();

        // Take turn
        gameSpace.put(playerId, "taken");

        // Do an action: Place or draw card
        doAction();

        TimeUnit.SECONDS.sleep(1);

        // End turn
        systemSpace.get(new ActualField("lock"));
        System.out.println("\nEnter to end turn");
        systemSpace.put("lock");
        scanner.nextLine();
        gameSpace.put(playerId, "ended");

        return true;
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
    StartClient startClient;

    public ClientBoard(String playerId, RemoteSpace gameSpace, SequentialSpace systemSpace, StartClient startClient) {
        this.playerId = playerId;
        this.gameSpace = gameSpace;
        this.systemSpace = systemSpace;
        this.startClient = startClient;
    }

    @Override
    public void run() {
        try {
            while (!startClient.gameDone) {

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
    StartClient startClient;

    public ClientHand(String playerId, RemoteSpace gameSpace, SequentialSpace systemSpace, StartClient startClient) {
        this.playerId = playerId;
        this.gameSpace = gameSpace;
        this.systemSpace = systemSpace;
        this.startClient = startClient;
    }

    @Override
    public void run() {
        try {
           while (!startClient.gameDone) {

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
    StartClient startClient;

    public TurnWatcher(String playerId, RemoteSpace gameSpace, SequentialSpace systemSpace, StartClient startClient) {
        this.playerId = playerId;
        this.gameSpace = gameSpace;
        this.systemSpace = systemSpace;
        this.startClient = startClient;
    }

    @Override
    public void run() {
        try {
            while (!startClient.gameDone) {
                String player = (String) gameSpace.get(
                        new ActualField(playerId),
                        new ActualField("takes"),
                        new FormalField(String.class)
                )[2];

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

class ListenUNO implements Runnable{

    String playerId;
    RemoteSpace gameSpace;
    SequentialSpace systemSpace;
    StartClient startClient;

    public ListenUNO(String playerId, RemoteSpace gameSpace, SequentialSpace systemSpace, StartClient startClient) {
        this.playerId = playerId;
        this.gameSpace = gameSpace;
        this.systemSpace = systemSpace;
        this.startClient = startClient;
    }

    @Override
    public void run() {
        try {
            while (!startClient.gameDone) {

                // Wait for signal
                String caller = (String) gameSpace.get(
                        new ActualField(playerId),
                        new ActualField("UNO"),
                        new FormalField(String.class)
                )[2];

                // TUI
                systemSpace.get(new ActualField("lock"));
                if (caller.equals(playerId)) System.out.println("\nYou called UNO");
                else System.out.println("\n" + caller + " called UNO");
                System.out.flush();
                systemSpace.put("lock");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class ListenMissingUNO implements Runnable{

    String playerId;
    RemoteSpace gameSpace;
    SequentialSpace systemSpace;
    StartClient startClient;

    public ListenMissingUNO(String playerId, RemoteSpace gameSpace, SequentialSpace systemSpace, StartClient startClient) {
        this.playerId = playerId;
        this.gameSpace = gameSpace;
        this.systemSpace = systemSpace;
        this.startClient = startClient;
    }

    @Override
    public void run() {
        try {
            while (!startClient.gameDone) {

                // Wait for signal
                Object[] msg = gameSpace.get(
                        new ActualField(playerId),
                        new ActualField("UNO"),
                        new FormalField(String.class),
                        new FormalField(String.class)
                );

                String receiver = (String) msg[2];
                String caller = (String) msg[3];

                // TUI
                systemSpace.get(new ActualField("lock"));
                if (receiver.equals(playerId)) System.out.println("\n" + caller + " called missing UNO on you");
                else if (caller.equals(playerId)) System.out.println("\n You called missing UNO on " + receiver);
                else System.out.println("\n" + caller + " called missing UNO on " + receiver);
                System.out.flush();
                systemSpace.put("lock");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

