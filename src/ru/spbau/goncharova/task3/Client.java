package ru.spbau.goncharova.task3;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {

    protected final BufferedReader reader;
    protected final BufferedWriter writer;

    public static final String dataId = "data";
    public static final String statusId = "status";
    protected static final Random rand = new Random(System.currentTimeMillis());
    protected final int messageSize;
    protected final int messageCount;
    protected final Socket socket;

    public Client(String ipAddr, int port, int messageSize, int messageCount) throws IOException {
        InetAddress address = InetAddress.getByName(ipAddr);
        //connect socket
        socket = new Socket(address, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        this.messageSize = messageSize;
        this.messageCount = messageCount;
    }

    public String generateMessage() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < messageSize; ++i) {
            builder.append(rand.nextBoolean() ? '1' : '0');
        }
        return builder.toString();
    }

    public static long filterMean(List<Long> responseTimes) {
        long sum = 0;
        for (Long val: responseTimes) {
            sum += val;
        }
        if (responseTimes.size() > 1) {
            //calculate mean
            long mean = sum / responseTimes.size();
            //calculate dispersion
            double dispersion = 0;
            for (Long val : responseTimes) {
                dispersion += (val - mean) * (val - mean);
            }
            dispersion = dispersion / (responseTimes.size() - 1);
            //calculate deviation
            double deviation = Math.sqrt(dispersion);
            sum = 0;
            int successfulMessages = 0;
            //only take into account values that are less then (2 * deviation) away from mean
            for (Long val: responseTimes) {
                if (Math.abs(val - mean) < deviation * 2) {
                    sum += val;
                    successfulMessages++;
                }
            }
            return successfulMessages > 0 ? sum / successfulMessages : -1;

        } else if (responseTimes.size() > 0){
            return responseTimes.get(0);
        } else {
            return (long) -1;
        }
    }
}
