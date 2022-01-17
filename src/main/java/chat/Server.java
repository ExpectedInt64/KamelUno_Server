package chat;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jspace.FormalField;
import org.jspace.SequentialSpace;
import org.jspace.SpaceRepository;

import java.util.ArrayList;


public class Server {
    private SpaceRepository chatRepository = new SpaceRepository();
    private static Server single_instance = null;
    private ChatHandler chatHandler = new ChatHandler(chatRepository);

    private Server(){
        chatRepository.addGate("tcp://server:9001/?keep");
    }

    public static Server getInstance(){
        if (single_instance == null)
            single_instance = new Server();
        return single_instance;
    }
    public void start(){
        while (true){

        }
    }

    public void addChatRoom(){
        chatHandler.createChatRoom();
        System.out.println("Starting: "+ (chatHandler.chats.size()-1));
        new Thread(chatHandler.chats.get(chatHandler.chats.size()-1)).start();
    }
}

class ChatHandler{
    private int rooms = 0;
    ArrayList<ChatRoom> chats = new ArrayList<>();
    SpaceRepository chatRepository;
    public ChatHandler(SpaceRepository chatRepository){
        this.chatRepository = chatRepository;
    }
    public void createChatRoom(){
        ChatRoom chat = new ChatRoom(rooms);
        chatRepository.add("chat"+chat.getRoomId(),chat.getChat());
        chats.add(chat);
        System.out.println("Chatroom: chat"+chat.getRoomId() + " added!");
        rooms++;
    }
}
class ChatRoom implements Runnable{
    private final SequentialSpace chat = new SequentialSpace();
    private final int roomId;
    private ArrayList<String> chatters = new ArrayList<>();
    public ChatRoom(int roomId){
        this.roomId = roomId;
    }

    public SequentialSpace getChat() {
        return chat;
    }

    public int getRoomId() {
        return roomId;
    }
    @Override
    public void run() {
        new Thread(new UserHandler(chatters,chat)).start();
        while (true) {
            try{
                Object[] t = chat.get(new FormalField(String.class), new FormalField(String.class));
                System.out.println("Chat"+roomId+": "+t[0] + ":" + t[1]);
                for(String u : chatters){
                    chat.put(u, t[0], t[1]);
                }
            }catch (InterruptedException e){

            }
        }
    }
}

class UserHandler implements Runnable{
    private ArrayList<String> chatters;
    private SequentialSpace chat;
    public UserHandler(ArrayList<String> chatters,SequentialSpace chat){
        this.chatters=chatters;
        this.chat=chat;
    }
    @Override
    public void run() {
        while (true){
            try {
                Object[] T = chat.get(new FormalField(String.class));
                if(!chatters.contains((String)T[0]))
                {
                    System.out.println(T[0] + " has joined the chat!");
                    chat.put("Server", T[0] + " has joined the chat!");
                    chatters.add((String)T[0]);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

