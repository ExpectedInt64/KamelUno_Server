import chat.Server;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello World!");
        Server.getInstance().addChatRoom();
        Server.getInstance().addChatRoom();
        System.out.println("Hello from bottom");
    }
}
