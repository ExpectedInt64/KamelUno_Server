package chat;
import game.GameHandler;
import lombok.SneakyThrows;
import org.jspace.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

public class LobbyManager implements Runnable {
    private Space server_LobbyManager;
    private SpaceRepository spaceRepository;
    private SequentialSpace lobbies;

    public LobbyManager(Space server_LobbyManager, SpaceRepository spaceRepository) {
        this.server_LobbyManager = server_LobbyManager;
        this.spaceRepository = spaceRepository;
        lobbies = new SequentialSpace();
    }

    @Override
    public void run() {
        //Lobby Id is integer starting from 0.
        int lobbyID = 0;
        String lobbyURI;
        while (true) {
            try {
                //LobbyManager server requests coming from server with template (String, String)
                Object[] request = server_LobbyManager.get(new FormalField(String.class), new FormalField(String.class));
                String requestType = (String) request[0];
                String requestArgument = (String) request[1];
                System.out.println("Processing request at LobbyManager " + requestType + " -> " + requestArgument);

                // 3 request types are served looking at first element in tuple.
                switch (requestType) {

                    //Creates a lobby
                    case "createLobby":
                        lobbyURI = "tcp://127.0.0.1:9001/lobby" + lobbyID + "?keep";
                        System.out.println("Setting up lobby space " + lobbyURI + "...");
                        SequentialSpace lobby = new SequentialSpace();
                        spaceRepository.add("lobby" + lobbyID, lobby);
                        //lobbywaiter is started with a new thread thus making every lobby as a private space and private waiter.
                        new Thread(new lobbyWaiter(lobby, lobbyID,spaceRepository)).start();
                        lobbies.put("" + lobbyID, 1);
                        lobbyID++;
                        server_LobbyManager.put(requestType, requestArgument, "if");
                        server_LobbyManager.put(requestType, requestArgument, "oklobby");
                        break;

                    //Checks that a Client can or cannot join a lobby
                    case "joinLobby":
                        Object[] the_lobby2 = lobbies.queryp(new ActualField(requestArgument), new FormalField(Integer.class));
                        if (the_lobby2 != null) {
                            System.out.println("The lobby is found sending URL");
                            int numOfPlayer = (int) the_lobby2[1];
                            if (numOfPlayer < 5) {
                                lobbyURI = "tcp://127.0.0.1:9001/lobby" + the_lobby2[0] + "?keep";
                                server_LobbyManager.put(requestType, requestArgument, "if");
                                server_LobbyManager.put(requestType, requestArgument, "oklobby");
                                Object[] updateLobby = lobbies.get(new ActualField(requestArgument), new FormalField(Integer.class));
                                int increasesize = (int) updateLobby[1];
                                increasesize++;
                                lobbies.put(updateLobby[0], increasesize);

                            } else {
                                System.out.println("The lobby is full. Sending error response.");
                                server_LobbyManager.put(requestType, requestArgument, "else");
                                server_LobbyManager.put(requestType, requestArgument, "koybbol");
                            }
                        } else {
                            System.out.println("The lobby " + requestArgument + " does not exist. Sending error response.");
                            server_LobbyManager.put(requestType, requestArgument, "else");
                            server_LobbyManager.put(requestType, requestArgument, "koybbol");
                        }
                        break;

                    //Sends lobbies that were created.
                    case "getLobbies":
                        LinkedList<Object[]> lobbies = this.lobbies.queryAll(new FormalField(String.class), new FormalField(Integer.class));
                        String[] list = new String[lobbies.size()];
                        System.out.println(lobbies.size());
                        for (int i = 0; i < lobbies.size(); i++) {
                            String value = (String) lobbies.get(i)[0];
                            System.out.println(value);
                            list[i] = value;
                        }
                        server_LobbyManager.put("getLobbies", list);
                        break;
                    default:

                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}

/**
 *  lobbyWaiter serves four different functionalities in a lobby:
 *  * Adds Clients to players array.
 *  * Returns all players in a string Array.
 *  * Broadcasts a message to all players.
 *  * Initializes gameHandler.
 */
class lobbyWaiter implements Runnable {

    private Space lobby;
    private SpaceRepository spaceRepository;
    private int lobbyID;
    private ArrayList<String> players;

    public lobbyWaiter(Space lobby, int lobbyID, SpaceRepository spaceRepository) {
        this.lobby = lobby;
        this.lobbyID = lobbyID;
        this.spaceRepository = spaceRepository;
        this.players = new ArrayList<String>();
    }

    @Override
    public void run() {
        while (true) {
            try {
                Object[] t = lobby.get(new FormalField(String.class), new FormalField(String.class));
                String msg1 = (String) t[0];
                String msg2 = (String) t[1];

                if (msg1.equals("joined")) {

                    System.out.println("Lobby" + lobbyID + ": " + msg2 + " has " + msg1);
                    players.add(msg2);
                    lobby.put(msg2, "has joined.");

                } else if (msg1.equals("getPlayers")) {

                    System.out.println("Lobby" + lobbyID + ": get Players requested.");
                    String[] listofplayers = players.toArray(String[]::new);
                    System.out.println(Arrays.toString(listofplayers));
                    lobby.put(Arrays.toString(listofplayers));

                } else if(msg1.equals("initGame")){

                    System.out.println("Lobby" + lobbyID + ": initGame requested. Starting gameHandler in a thread.");
                    SequentialSpace gameSpace = new SequentialSpace();
                    spaceRepository.add("game"+lobbyID,gameSpace);
                    String[] listOfPlayers = new String[players.size()];
                    for (int i = 0; i < players.size(); i++) {
                        listOfPlayers[i] = players.get(i);
                    }
                    Thread thread = new Thread(){
                        @SneakyThrows
                        public void run(){
                            new GameHandler(spaceRepository, gameSpace,listOfPlayers);
                        }
                    };thread.start();

                    for (String player : players) {
                        lobby.put(player, "System", "Go!","");
                    }

                } else {
                    System.out.println("Lobby" + lobbyID + ": " + t[0] + ":" + t[1]);
                    for (String player : players) {
                        lobby.put(player, t[0], t[1]);
                    }
                }
            } catch (InterruptedException e) {

            }
        }
    }
}