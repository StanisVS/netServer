package ru.spbau.goncharova.task3;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SplitClient extends Client implements Callable<Integer> {

    private boolean isWrite = true;
    private int currentMessageCount = 0;
    private int successfulMessageCount = 0;
    private final ExecutorService threadPool;
    private final LinkedList<Long> responseTimes = new LinkedList<Long>();
    private long startTime = -1;
    private final AtomicInteger finishedClients;

    public SplitClient(String ipAddr, int port, int messageSize, int messageCount, ExecutorService threadPool, AtomicInteger atomicInteger) throws IOException {
        super(ipAddr, port, messageSize, messageCount);
        this.threadPool = threadPool;
        this.finishedClients = atomicInteger;
    }

    @Override
    public Integer call() throws Exception {
        if (!socket.isConnected()) {
            System.err.println("not connected socket");
        }
        if (startTime <= 0) {
            startTime = System.nanoTime();
        }
        if (isWrite) {
            String message = generateMessage();
            JSONObject object = new JSONObject();
            object.put(dataId, message);
            //send request
            writer.write(object.toJSONString() + "endl");
            writer.flush();
        } else {
            String responseString = reader.readLine();
            long afterRequestTime = System.nanoTime();
            long responseTime = afterRequestTime - startTime;
            JSONParser parser = new JSONParser();
            JSONObject response = (JSONObject) parser.parse(responseString);
            //the request has been processed by the server
            ProcessingResult result = new ProcessingResult(response, responseTime);
            if (result.isOk()) {
                responseTimes.add(responseTime);
                successfulMessageCount++;
            }
            //message is processed
            currentMessageCount++;
            startTime = System.nanoTime();
        }
        isWrite = !isWrite;
        if (currentMessageCount < messageCount) {
            threadPool.submit(this);
        } else {
            finishedClients.incrementAndGet();
        }
        return 0;
    }

    public static void runTest(int clientsCount, int messageSize, int messageCount, String ipAddress, int port) throws InterruptedException {
        ExecutorService threadPool = Executors.newFixedThreadPool(clientsCount);
        SplitClient[] clients = new SplitClient[clientsCount];
        AtomicInteger waiter = new AtomicInteger(0);
        for (int i = 0; i < clientsCount; ++i) {
            try {
                final SplitClient myClient = new SplitClient(ipAddress, port, messageSize, messageCount, threadPool, waiter);
                clients[i] = myClient;
                threadPool.submit(myClient);
            } catch (IOException e) {
                System.err.println("Failed to connect client number " + i);
                e.printStackTrace();
            }
        }
//        threadPool.awaitTermination(100, TimeUnit.DAYS);
//        threadPool.shutdown();
        while (waiter.get() < clientsCount) {}
        threadPool.shutdown();
        int successfulClients = 0;
        long sum = 0;
        for (SplitClient client : clients) {
            if (client.successfulMessageCount > 0) {
                successfulClients++;
                sum += filterMean(client.responseTimes);
            }
        }
        System.out.println(successfulClients + "," + sum / successfulClients);
    }

    public static void main(String[] args) throws FileNotFoundException, InterruptedException {
        if (args.length < 5) {
            System.out.println("Not enough arguments.");
            System.out.println("Usage: MyClient clientsCount messageLength messagesCount ipAddress port");
        } else {
            final int clientCount = Integer.parseInt(args[0]);
            final int messageSize = Integer.parseInt(args[1]);
            final int messageCount = Integer.parseInt(args[2]);
            final String ipAddress = args[3];
            final int port = Integer.parseInt(args[4]);
            for (int i = clientCount; i < 500; i += 10) {
                runTest(i, messageSize, messageCount, ipAddress, port);
            }
        }
    }
}
