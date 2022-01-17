import chat.PlayerManager;
import chat.Server;
import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.SequentialSpace;
import org.jspace.SpaceRepository;

import java.sql.Struct;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        final String PORT = "9001";

        System.out.println("Server starting at " + PORT);
        SpaceRepository kamelUnoServerRepository = new SpaceRepository();
        SequentialSpace requestSpace = new SequentialSpace();
        SequentialSpace serverToPlayerManager = new SequentialSpace();
        SequentialSpace serverToLobbyManager = new SequentialSpace();
        kamelUnoServerRepository.add("requestSpace",requestSpace);
        kamelUnoServerRepository.addGate("tcp://server:" + PORT + "/?keep");
        List<String> players = new ArrayList<>();
        List<String> lobbies = new ArrayList<>();

        new Thread(new PlayerManager(serverToPlayerManager,players)).start();
        new Thread(new PlayerManager(serverToLobbyManager,players)).start();

        while (true){
            try {
                Object[] request = requestSpace.get(new FormalField(String.class), new FormalField(String.class));
                String requestType = (String) request[0];
                String requestArgument = (String) request[1];

                switch (requestType){
                    case "addPlayer":
                        serverToPlayerManager.put(requestArgument);
                        break;
                    case "joinChatRoom":
                        serverToLobbyManager.put(requestArgument);
                        break;
                    default:
                        System.out.println("request type not found");
                }

            }catch (Exception e){
                e.printStackTrace();
            }
        }

        //Server.getInstance().addChatRoom();
        //Server.getInstance().addChatRoom();
        //System.out.println("Hello from bottom")


    }
}
