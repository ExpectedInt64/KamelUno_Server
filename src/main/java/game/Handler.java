package game;

import org.jspace.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// todo what if deck is empty
// todo the players should always listen for a missing UNO penalty
// todo the players should always listen for new cards to draw
// todo the players should always updates to the board (like new stack card)

// todo as of now a player can always draw a card, even if a play action is available.
//  This means a player can potentially keep drawing cards and the game can end up in unstable situations

// todo notify player every time gameboard updates

// todo Send rækkefølgen med


/*
General template: ("playerId", "command", "payload")

SERVER TO CLIENT COMMANDS
- (playerId, "start"): The player can take his turn
- (playerId, "card", Card): A card is ready for the player to draw
- (playerId, "invalid"): The played card was invalid
- (playerId, "success": The card was played successfully
- (playerId, "board", Board): The board was updated

CLIENT TO SERVER COMMANDS
- (playerId, "end"): The players ends his turn
- (playerId, "begin"): The players takes his turn
- (playerId, "action", Action): The player performs an action (play or draw card)
 */


// Given a game-space this class handles it for the players
public class Handler {

    SpaceRepository gameRepository = new SpaceRepository(); // The repository through which the players communicate
    SequentialSpace gameSpace; // The space through which the players communicate

    String[] playerIds;  // List of all the player's ids
    int currentPlayer = 0;  // The index of the current player to take turn
    int previousPlayer;  // The index of the last player to take turn
    RandomSpace deck = new RandomSpace();  // The deck from which the players can draw cards
    StackSpace stack = new StackSpace();  // The stack in which the players place their cards. Top card is available with a queryp
    boolean reverse = false;  // True if the order in which the players take turn should be reversed
    boolean missingUNO = false;  // True if the last player forgot to say UNO
    boolean skipNextPlayer = false;  // True if a skip card has been played and the next player should be skipped
    int penalty = 0;  // The amount of penalty the next player is going to receive
    boolean turnDone = false;  // A player only gets one action per turn (draw or play a card)
    Map<String, ArrayList<Card>> hands;  // To keep track of what cards each player has on his hand

    // Constructor
    public Handler(String gameId, SequentialSpace gameSpace, String[] playerIds) throws InterruptedException {

        this.playerIds = playerIds;
        this.gameSpace = gameSpace;

        // Make game-space available to players
        gameRepository.add(gameId, this.gameSpace);
        gameRepository.addGate("tcp://localhost:31415/?keep");

        // Needed before manipulating shared variables
        gameSpace.put("lock");

        // Build the board
        initBoard();

        // Start the game
        initGame();

        // Start listening
        listen();
    }

    // Results in the deck being filled with tuples like ("red", "3")
    private void initBoard() throws InterruptedException {
        int numberOfCards = 52;

        String[] colors = {
                "Red", "Red", "Red", "Red", "Red", "Red", "Red", "Red", "Red", "Red", "Red", "Red",
                "Yellow", "Yellow", "Yellow", "Yellow", "Yellow", "Yellow", "Yellow", "Yellow", "Yellow", "Yellow", "Yellow", "Yellow",
                "Blue", "Blue", "Blue", "Blue", "Blue", "Blue", "Blue", "Blue", "Blue", "Blue", "Blue", "Blue",
                "Green", "Green", "Green", "Green", "Green", "Green", "Green", "Green", "Green", "Green", "Green", "Green",
                "Black", "Black", "Black", "Black"
        };

        String[] values = {
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "Skip", "Draw", "Reverse",
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "Skip", "Draw", "Reverse",
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "Skip", "Draw", "Reverse",
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "Skip", "Draw", "Reverse",
                "Color", "Color", "Draw", "Draw"
        };

        assert colors.length == numberOfCards;
        assert values.length == numberOfCards;

        for (int i = 0; i < 52; i++) {
            deck.put(new Card(colors[i], values[i]));
        }

        // Flip the first card to the stack
        stack.put(getRandomCardFromDeck());
    }

    // Initialize the game
    private void initGame() throws InterruptedException {

        // Provide players with cards
        for (int i = 0; i < playerIds.length; i++) {
            hands.put(playerIds[i], new ArrayList<>());
            sendRandomCards(playerIds[i], 7);
        }

        // Send the board for the players to display
        sendBoard();

        // Notify first player to start
        gameSpace.put(playerIds[currentPlayer], "start");
    }

    private void listen() throws InterruptedException {
        while(true){
            takeTurn();
            takeAction();
            nextPlayer();
        }
    }

    // Take turn (apply penalty)
    private void takeTurn() throws InterruptedException {
        String playerId = (String) gameSpace.get(new FormalField(String.class), new ActualField("begin"))[0];

        // Check playerId
        if (!isCurrentPlayer(playerId)) return;

        // Apply penalty  if any
        if (penalty > 0) {
            sendRandomCards(playerId, penalty);
            penalty = 0;

            // Update player's board
            sendBoard();
        }
    }

    private void takeAction() throws InterruptedException {
        // A player can only do one action per turn
        if (turnDone) return;

        Object[] request = gameSpace.get(
                new FormalField(String.class),
                new ActualField("action"),
                new FormalField(Action.class)
        );

        String playerId = (String) request[0];
        Action action = (Action) request[2];

        // Check playerId
        if (!isCurrentPlayer(playerId)) return;

        // If a card was played
        if (action.getAction().equals(Actions.PLAY))
            playACard(playerId, action.getCard());

        // If the player chose to draw a card
        if (action.getAction().equals(Actions.DRAW))
            drawACard(playerId);

        // Disable possibility for more actions
        turnDone = true;
    }

    // Notify next player (increment currentPlayer and previousPlayer)
    private void nextPlayer() throws InterruptedException {

        // Wait for the current player to end his turn
        String playerId = (String) gameSpace.get(new FormalField(String.class), new ActualField("end"))[0];

        // Check playerId
        if (!isCurrentPlayer(playerId)) return;

        // Update previous player
        previousPlayer = currentPlayer;

        // Update current player
        int increment = 1;
        if (skipNextPlayer) {
            increment++;
            skipNextPlayer = false;
        }

        if (!reverse) currentPlayer = (currentPlayer + increment) % playerIds.length;
        if (reverse) currentPlayer = ((currentPlayer - increment) + playerIds.length) % playerIds.length;

        // Enable move for next player
        turnDone = false;

        // Notify next player to start
        gameSpace.put(playerIds[currentPlayer], "start");
    }

    // Play a card (disable UNO, save penalty, respond with status)
    private void playACard(String playerId, Card card) throws InterruptedException {

        // Check the move is valid
        if (!isMoveValid(card) || !isPlayersCard(playerId, card)) {
            gameSpace.put(playerId, "invalid");
            return;
        }

        // Add the card to the stack
        stack.put(card);

        // Remove the card from the players hand
        removeCardFromPlayer(playerId, card);

        // Reset missing UNO
        mutualExclusion(() -> {
            missingUNO = false;
        });

        // If reverse
        if (card.value.equals("Reverse")) reverse = !reverse;

        // If skip
        if (card.value.equals("Skip")) skipNextPlayer = true;

        // If penalty
        if (card.value.equals("Draw")) {
            if (card.color.equals("Black")) penalty = 4;
            else penalty = 2;
        }

        // Notify players of change
        sendBoard();

        // Respond with success
        gameSpace.put(playerId, "success");
    }

    // Removes a card from a player's hand
    private void removeCardFromPlayer(String playerId, Card card) {
        ArrayList<Card> playerHand = hands.get(playerId);

        // Loop through cards on player's hand to find match
        for (int i = 0; i < playerHand.size(); i++) {
            Card cardOnHand = playerHand.get(i);

            // If there is a match
            if (cardOnHand.equals(card)) {
                playerHand.remove(i);
                return;
            }
        }
    }

    // Check whether a given card exists on a player's hand
    private boolean isPlayersCard(String playerId, Card claimCard) {
        ArrayList<Card> playerHand = hands.get(playerId);

        // Loop through cards on player's hand to search for match
        for (int i = 0; i < playerHand.size(); i++) {
            Card trueCard = playerHand.get(i);

            // If there is a match
            if (trueCard.equals(claimCard)) return true;
        }

        return false;
    }

    // Send the board to all the players
    private void sendBoard() throws InterruptedException {

        Card topCard = (Card) stack.queryp(new FormalField(Card.class))[0];

        // Count number of cards on each player's hand
        Map<String, Integer> handsCount = new HashMap<>();
        for (Map.Entry<String, ArrayList<Card>> entry : hands.entrySet())
            handsCount.put(entry.getKey(), entry.getValue().size());

        Board board = new Board(topCard, handsCount);

        // Send the new board to the players
        for (int i = 0; i < playerIds.length; i++)
            gameSpace.put(playerIds[i], "board", board);
    }

    // Allow a player to draw a random card from the deck
    private void drawACard(String playerId) throws InterruptedException {
        Card card = getRandomCardFromDeck();

        // Send card to player
        gameSpace.put(playerId, "card", card);

        // Add card to players hans
        hands.get(playerId).add(card);

        // Notify players of change
        sendBoard();
    }

    // Sends a certain amount of random cards to a player drawn from the deck
    private void sendRandomCards(String playerId, int numberOfCards) throws InterruptedException {
        for (int i = 0; i < numberOfCards; i++) {
            Card card = getRandomCardFromDeck();
            gameSpace.put(playerId, "card", card);
            hands.get(playerId).add(card);
        }
    }

    // Draw a random card from the deck
    private Card getRandomCardFromDeck() throws InterruptedException {

        // If the deck is empty flip the stack
        if (deck.size() < 1) flipTheStack();

        return (Card) deck.getp(new FormalField(Card.class))[0];
    }

    // Checks if a giving player ID is the one currently playing
    private boolean isCurrentPlayer(String playerId) {
        return playerId.equals(playerIds[currentPlayer]);
    }

    // Check is a certain move is valid
    private boolean isMoveValid(Card card) {

        Card topCard = (Card) stack.queryp(new FormalField(Card.class))[0];

        // If the card is black, the move is always valid
        if (card.color.equals("Black")) return true;

        // If the colors match, the move is valid
        if (card.color.equals(topCard.color)) return true;

        // If the values match, the move is valid
        if (card.value.equals(topCard.value)) return true;

        // Else the move is invalid
        return false;
    }

    // Only one at a time is allowed access to the gameSpace through mutualExclusion
    private void mutualExclusion(Callable callable) throws InterruptedException {
        gameSpace.get(new ActualField("lock"));
        callable.call();
        gameSpace.put("lock");
    }

    interface Callable {
        public void call();
    }

    // If there are no more cards in the deck, the stack needs to be added back
    private void flipTheStack() throws InterruptedException {

        // Save the top card to keep in stack
        Card topCard = (Card) stack.getp(new FormalField(Card.class))[0];

        // Get the others cards from the stack and add them back to the deck
        while(stack.size() > 0) {
            Card card = (Card) stack.getp(new FormalField(Card.class))[0];
            deck.put(card);
        }

        stack.put(topCard);
    }

    // Call UNO

    // Call missing UNO (apply penalty)

    // Update game board
}

// A template for a single card in the deck
class Card {
    String color;
    String value;

    public Card(String color, String value) {
        this.color = color;
        this.value = value;
    }

    public String getColor() { return color; }
    public String getValue() { return value; }

    public boolean equals(Card card) {
        return (this.color.equals(card.color) &&
                this.value.equals(card.value));
    }
}

// Actions a player can preform during a round
enum Actions { PLAY, DRAW }

// Used to communicate what action a player wants to preform
// Allows us to listen for a general action on a tuple space
// and later preform logic, rather than having to listen for
// two separate potential actions
class Action {
    private Actions action;
    private Card card;

    public Action(Actions action, Card card) {
        this.action = action;
        this.card = card;
    }

    public Actions getAction() { return action; }
    public Card getCard() { return card; }
}

class Board {
    Card topCard;
    Map<String, Integer> hands;

    public Board(Card topCard, Map<String, Integer> playerHands) {
        this.topCard = topCard;
        this.hands = playerHands;
    }

    public Card getTopCard() { return topCard; }
    public Map<String, Integer> getPlayerHands() { return hands; }
}

class run {
    public static void main(String[] args) throws InterruptedException, IOException {
        new Handler("gameId", new SequentialSpace(), new String[]{"Mikkel", "Volkan"});

        RemoteSpace gameSpace = new RemoteSpace("tcp://localhost:31415/gameId?keep");
//        new Thread(new Alice(gameSpace)).start();

    }
}

class Alice implements Runnable {
    RemoteSpace gameSpace;

    public Alice(RemoteSpace gameSpace) {
        this.gameSpace = gameSpace;
    }

    @Override
    public void run() {

        while (true) {
            try {
                Card msg = (Card) gameSpace.get(
                        new ActualField("Mikkel"),
                        new FormalField(Card.class)
                )[1];
                System.out.println(msg.color + " " + msg.value);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

