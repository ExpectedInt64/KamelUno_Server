import chat.LobbyManager;
import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.SequentialSpace;
import org.jspace.SpaceRepository;

public class Main {
    public static void main(String[] args) {
        final int PORT = 9001;
        //Server Space Repository and the space between server and client
        SpaceRepository kamelUnoServerRepository = new SpaceRepository();
        SequentialSpace requestSpace = new SequentialSpace();
        SequentialSpace serverToLobbyManager = new SequentialSpace();
        kamelUnoServerRepository.add("requestSpace",requestSpace);

        System.out.println("Server starting at port: " + PORT);
        kamelUnoServerRepository.addGate("tcp://server:" + PORT + "/?keep");

        //Start LobbyManager in another thread.
        new Thread(new LobbyManager(serverToLobbyManager,kamelUnoServerRepository)).start();

        while (true){
            try {
                //Listen to request's from Clients. Template: (String,String,String)
                Object[] request = requestSpace.get(new FormalField(String.class), new FormalField(String.class), new FormalField(String.class));
                String requestType = (String) request[0];
                String requestVerb = (String) request[1];
                String requestArgument = (String) request[2];
                System.out.println("Serving request " + requestType + " : " + requestVerb + " -> " + requestArgument);

                //For now server serves only requests to LobbyManager
                if ("lobby".equals(requestType)) {

                    //This requestVerb returns all lobbies that were created. Output: String array.
                    if (requestVerb.equals("getLobbies")) {
                        System.out.println("Sending getlobbies to lobbymanager");
                        serverToLobbyManager.put(requestVerb, requestArgument);
                        Object[] response = serverToLobbyManager.get(new ActualField("getLobbies"), new FormalField(String[].class));
                        requestSpace.put(response[0], response[1]);
                        System.out.println("done");
                    } else {
                        //Other requests such as create and join lobby is processed here and the relevant response sendt to Client.
                        serverToLobbyManager.put(requestVerb, requestArgument);
                        Object[] response = serverToLobbyManager.get(new FormalField(String.class), new FormalField(String.class), new FormalField(String.class));
                        String the_if = (String) response[2];
                        //Success response with if
                        if (the_if.equals("if")) {
                            Object[] response2client = serverToLobbyManager.get(new FormalField(String.class), new FormalField(String.class), new ActualField("oklobby"));
                            requestSpace.put(response2client[2]);
                        } else {
                            //Error response
                            Object[] response2client = serverToLobbyManager.get(new FormalField(String.class), new FormalField(String.class), new ActualField("koybbol"));
                            requestSpace.put(response2client[2]);
                        }
                    }
                } else {
                    System.out.println("request type not found");
                    requestSpace.put("koybbol");
                }

            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}