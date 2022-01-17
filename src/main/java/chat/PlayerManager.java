package chat;

import org.jspace.FormalField;
import org.jspace.Space;

import java.util.List;

public class PlayerManager implements Runnable{
    private Space space;
    private List<String> players;

    public PlayerManager(Space space, List<String> players) {
        this.space = space;
        this.players = players;
    }

    @Override
    public void run() {
        while (true){
            try {
                Object[] t = space.get(new FormalField(String.class));
                String name = (String) t[0];
                System.out.println(name);
                players.add(name);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
