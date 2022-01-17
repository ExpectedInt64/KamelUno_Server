package chat;

import org.jspace.Space;

import java.util.List;

public class LobbyManager implements Runnable{
    private Space space;
    private List<String> players;

    public LobbyManager(Space space, List<String> players) {
        this.space = space;
        this.players = players;
    }

    @Override
    public void run() {

    }
}
