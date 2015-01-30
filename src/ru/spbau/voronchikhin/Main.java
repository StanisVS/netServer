package ru.spbau.voronchikhin;

/**
 * Created by s on 26.01.15.
 */
public class Main {
    public static void main(String[] args) {
        Server server = new Server(1234);
        server.run();
    }
}
