package chat;

import org.jspace.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LobbyManager implements Runnable{
    private Space server_LobbyManager;
    private List<String> players;
    private SpaceRepository spaceRepository;
    private SequentialSpace lobbies;

    public LobbyManager(Space server_LobbyManager, SpaceRepository spaceRepository, List<String> players) {
        this.server_LobbyManager = server_LobbyManager;
        this.players = players;
        this.spaceRepository = spaceRepository;
        lobbies = new SequentialSpace();
    }

    @Override
    public void run() {

        while (true){
            int lobbyID = 0;
            String lobbyURI;

            try{
                Object[] request = server_LobbyManager.get(new FormalField(String.class), new FormalField(String.class));
                String requestType = (String) request[0];
                String requestArgument = (String) request[1];
                System.out.println("Processing request at LobbyManager " + requestType + " -> " + requestArgument);
                switch (requestType){
                    case "createLobby":
                        Object[] the_lobby = lobbies.queryp(new ActualField(requestArgument),new FormalField(Integer.class));
                        if (the_lobby != null) {
                            System.out.println("The lobby does exist. Sending error response.");
                            server_LobbyManager.put(requestType,requestArgument,"else");
                            server_LobbyManager.put(requestType,requestArgument,"koybbol");
                        }else {
                            lobbyURI = "tcp://127.0.0.1:9001/lobby" + lobbyID + "?keep";
                            System.out.println("Setting up lobby space " + lobbyURI + "...");
                            SequentialSpace lobby = new SequentialSpace();
                            spaceRepository.add("lobby" + lobbyID, lobby);
                            new Thread(new lobbyWaiter(lobby, lobbyID)).start();
                            lobbies.put(lobbyID,1);
                            lobbyID++;
                            server_LobbyManager.put(requestType,requestArgument,"if");
                            server_LobbyManager.put(requestType,requestArgument,"oklobby");
                        }
                        break;
                    case "joinLobby":
                        Object[] the_lobby2 = lobbies.queryp(new ActualField(requestArgument),new FormalField(Integer.class));
                        if (the_lobby2 != null) {
                            System.out.println("The lobby is found sending URL");
                            int numOfPlayer = (int) the_lobby2[1];
                            if(numOfPlayer < 4) {
                                lobbyURI = "tcp://127.0.0.1:9001/lobby" + the_lobby2[0] + "?keep";
                                server_LobbyManager.put(requestType, requestArgument, "if");
                                server_LobbyManager.put(requestType, requestArgument, "oklobby");
                                Object[] updateLobby = lobbies.get(new ActualField(requestArgument),new FormalField(Integer.class));
                                int increasesize = (int) updateLobby[1];
                                increasesize++;
                                lobbies.put(updateLobby[0],increasesize);

                            }else{
                                System.out.println("The lobby does exist. Sending error response.");
                                server_LobbyManager.put(requestType,requestArgument,"else");
                                server_LobbyManager.put(requestType,requestArgument,"koybbol");
                            }
                        }else{
                            System.out.println("The lobby does exist. Sending error response.");
                            server_LobbyManager.put(requestType,requestArgument,"else");
                            server_LobbyManager.put(requestType,requestArgument,"koybbol");
                        }
                        break;
                    case "getLobbies":
                        LinkedList<Object[]> lobbies = this.lobbies.queryAll(new FormalField(Integer.class), new FormalField(Integer.class));
                        server_LobbyManager.put("getLobbies",lobbies);
                        break;
                    default:

                }

            }catch (Exception e){
                e.printStackTrace();
            }


        }

    }
}

class lobbyWaiter implements Runnable {

    private Space lobby;
    private int lobbyID;
    private ArrayList<String> players;

    public lobbyWaiter(Space lobby,int lobbyID) {
        this.lobby = lobby;
        this.lobbyID = lobbyID;
        this.players = new ArrayList<>();
    }

    @Override
    public void run() {
        while (true) {
            try{
                Object[] t = lobby.get(new FormalField(String.class), new FormalField(String.class));
                String msg1 = (String) t[0];
                String msg2 = (String) t[1];
                if(msg2.equals("joined")){
                    System.out.println("Lobby"+lobbyID+": "+msg1 + " has joined " + msg2);
                    players.add(msg1);
                }else{
                    System.out.println("Lobby"+lobbyID+": "+t[0] + ":" + t[1]);
                    for(String player : players){
                        lobby.put(player, t[0], t[1]);
                    }
                }


            }catch (InterruptedException e){

            }
        }
    }
}