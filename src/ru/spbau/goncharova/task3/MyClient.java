package ru.spbau.goncharova.task3;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyClient extends Client implements Callable<Long> {

    private final AtomicBoolean barrier;

    public MyClient(String ipAddr, int port, int messageSize, int messageCount, AtomicBoolean barrier) throws IOException {
        super(ipAddr, port, messageSize, messageCount);
        this.barrier = barrier;
    }

    public ProcessingResult processMessage(String message, long beforeRequestTime) {
        JSONObject object = new JSONObject();
        object.put(dataId, message);
        String responseString = null;
        try {
            //send request
            writer.write(object.toJSONString() + "endl");
            writer.flush();
            //accept response
            responseString = reader.readLine();
            long afterRequestTime = System.nanoTime();
            long responseTime = afterRequestTime - beforeRequestTime;
            JSONParser parser = new JSONParser();
            JSONObject response = (JSONObject) parser.parse(responseString);
            //the request has been processed by the server
            return new ProcessingResult(response, responseTime);
        } catch (IOException e) {
            System.err.println("IOException when trying to process message " + message);
            e.printStackTrace();
            return null;
        } catch (ParseException e) {
            System.err.println("ParseException when trying to parse JSON response from server " + responseString);
            e.printStackTrace();
            return null;
        }
    }

    public static void runTest(int clientsCount, int messageSize, int messageCount, String ipAddress, int port) {
        ExecutorService threadPool = Executors.newFixedThreadPool(clientsCount);
        Collection<Future<Long>> result = new LinkedList<Future<Long>>();
        AtomicBoolean barrier = new AtomicBoolean(true);
        for (int i = 0; i < clientsCount; ++i) {
            try {
                final MyClient myClient = new MyClient(ipAddress, port, messageSize, messageCount, barrier);
                result.add(threadPool.submit(myClient));
            } catch (IOException e) {
                System.err.println("Failed to connect client number " + i);
                e.printStackTrace();
            }
        }
        barrier.set(false);
        threadPool.shutdown();
        long totalTime = 0;
        int totalClients = 0;
        for (Future<Long> res : result) {
            try {
                Long pRes = res.get();
                if (pRes >= 0) {
                    ++totalClients;
                    totalTime += pRes;
                } else {
                    System.err.println("no succesfull response!");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        System.out.println(totalClients + "," + totalTime / totalClients);
    }

    public static void main(String[] args) throws FileNotFoundException {
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

    @Override
    public Long call() throws Exception {
        while (barrier.get()) {}
        long sum = 0;
        List<Long> responseTimes = new LinkedList<Long>();
        if (!socket.isConnected()) {
            System.err.println("not connected socket");
        }
        long beforeRequestTime = System.nanoTime();
        //receive all responses
        for (int i = 0; i < messageCount; ++i) {
            String mssg = generateMessage();
            ProcessingResult pRes = processMessage(mssg, beforeRequestTime);
            if (pRes.isOk()) {
                responseTimes.add(pRes.responseTime);
                //measure time between subsequent read operations so that there are no holes in client execution that are not covered by time measurements
                beforeRequestTime += pRes.responseTime;
            } else {
                beforeRequestTime = System.nanoTime();
            }
        }
        socket.close();
        return filterMean(responseTimes);
    }
}
