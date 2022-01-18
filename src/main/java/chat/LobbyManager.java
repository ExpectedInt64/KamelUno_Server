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
        int lobbyID = 0;
        String lobbyURI;
        while (true){
            try{
                Object[] request = server_LobbyManager.get(new FormalField(String.class), new FormalField(String.class));
                String requestType = (String) request[0];
                String requestArgument = (String) request[1];
                System.out.println("Processing request at LobbyManager " + requestType + " -> " + requestArgument);
                switch (requestType){
                    case "createLobby":
                        // TODO: skal man kunne oprette en lobby med given id eller skal man bare oprette som næste ledig id.
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
                            lobbies.put(""+lobbyID,1);
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
                                System.out.println("The lobby is full. Sending error response.");
                                server_LobbyManager.put(requestType,requestArgument,"else");
                                server_LobbyManager.put(requestType,requestArgument,"koybbol");
                            }
                        }else{
                            System.out.println("The lobby "+ requestArgument +" does not exist. Sending error response.");
                            server_LobbyManager.put(requestType,requestArgument,"else");
                            server_LobbyManager.put(requestType,requestArgument,"koybbol");
                        }
                        break;
                    case "getLobbies":
                        LinkedList<Object[]> lobbies = this.lobbies.queryAll(new FormalField(String.class), new FormalField(Integer.class));
                        String[] list = new String[lobbies.size()];
                        System.out.println(lobbies.size());
                        for (int i = 0; i < lobbies.size(); i++) {
                            String value = (String) lobbies.get(i)[0];
                            System.out.println(value);
                            list[i] = value;
                        }

                        server_LobbyManager.put("getLobbies",list);
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
        this.players = new ArrayList<String>();
    }

    @Override
    public void run() {
        while (true) {
            try{
                Object[] t = lobby.get(new FormalField(String.class), new FormalField(String.class));
                String msg1 = (String) t[0];
                String msg2 = (String) t[1];
                if(msg1.equals("joined")){
                    System.out.println("Lobby"+lobbyID+": "+msg2 + " has " + msg1);
                    players.add(msg2);
                    lobby.put(msg2,"has joined.");
                }else if (msg1.equals("getPlayers")){
                    System.out.println("Lobby"+lobbyID+": get Players requested.");
                    String[] listofplayers = players.toArray(String[]::new);
                    lobby.put(listofplayers);
                } else{
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