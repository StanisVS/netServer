package ru.spbau.voronchikhin;

/**
 * Created by s on 26.01.15.
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

public class Server {

    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    private final int serverPort;
    private ExecutorService threadPool = Executors.newFixedThreadPool(8);
    private final ByteBuffer buffer = ByteBuffer.allocate(8192);
    private final Map<SelectionKey, List<ByteBuffer>> outputData = new HashMap<>();
    private final Map<SelectionKey, Worker> unfinishedReads = new HashMap<>();
    private BlockingQueue<Worker> readyWokers = new LinkedBlockingDeque<>();

    public Server(int port) {
        this.serverPort = port;
    }

    public void run() {
        System.out.println("server started on port " + serverPort);
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(serverPort));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            while (true) {
                processReadyWorkers();
                if (selector.select() == 0) {
                    continue;
                }
                final Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (!key.isValid()) {
                        return;
                    } else if (key.isAcceptable()) {
                        accept(key);
                    } else if (key.isReadable()) {
                        read(key);
                    } else if (key.isWritable()) {
                        write(key);
                    }
                    keyIterator.remove();
                }
            }
        } catch (IOException e) {
            System.err.println("failed execution " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("was interrupted");
        } finally {
            try {
                if (serverSocketChannel != null) {
                    serverSocketChannel.close();
                }
                if (selector != null) {
                    selector.close();
                }
            } catch (IOException e) {
                System.err.println("cant close =( " + e.getMessage());
            }
        }
    }

    private void processReadyWorkers() throws InterruptedException {
        while (!readyWokers.isEmpty()) {
            Worker worker = readyWokers.take();
            if (!worker.key.isValid()) {
                continue;
            }
            final SelectionKey key = worker.key;
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            if (outputData.containsKey(key)) {
                outputData.get(key).add(worker.getOutput());
            } else {
                List<ByteBuffer> outBuffers = new LinkedList<>();
                outBuffers.add(worker.getOutput());
                outputData.put(key, outBuffers);
            }
        }
        selector.wakeup();
    }

    private void accept(final SelectionKey key) throws IOException {
        ServerSocketChannel clientChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = null;
        try {
            socketChannel = clientChannel.accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            System.err.println("Failed on acception :" + e.getMessage());
            key.cancel();
            if (socketChannel != null) {
                socketChannel.close();
            }
        }
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        int read = 0;
        boolean hasData = false;
        boolean finished = !unfinishedReads.containsKey(key);
        Worker worker = finished ? new Worker(key) : unfinishedReads.get(key);
        try {
            buffer.clear();
            while ((read = socketChannel.read(buffer)) > 0) {
                buffer.flip();
                worker.addData(buffer, read);
                hasData = true;
                buffer.clear();
            }
        } catch (IOException e) {
            System.err.println("Failed on reading :" + e.getMessage());
            key.cancel();
            socketChannel.close();
        }
        if (read == -1) {
            key.cancel();
            socketChannel.close();
            return;
        }
        if (!hasData) {
            return;
        }
        if (worker.read()) {
            threadPool.execute(worker);
            unfinishedReads.remove(key);
        } else {
            unfinishedReads.put(key, worker);
        }
    }

    private void write(SelectionKey selectionKey) throws IOException {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        if (!outputData.containsKey(selectionKey)) {//no data to write
            return;
        }
        try {
            List<ByteBuffer> sendBuffers = outputData.get(selectionKey);
            Iterator<ByteBuffer> it = sendBuffers.iterator();
            while (it.hasNext()) {
                ByteBuffer byteBuffer = it.next();
                while (socketChannel.write(byteBuffer) > 0) {
                }
                if (byteBuffer.remaining() == 0) {
                    it.remove();
                } else
                    return;
            }
            if (sendBuffers.isEmpty()) {
                selectionKey.interestOps(SelectionKey.OP_READ);
                outputData.remove(selectionKey);
            }
        } catch (IOException e) {
            System.err.println("Write exception : " + e.getMessage());
            selectionKey.cancel();
            socketChannel.close();
        }
    }

    private class Worker implements Runnable {
        private byte[] buffer = new byte[8192];
        private int bufSize = 0;
        public final SelectionKey key;
        private ByteBuffer output;

        public Worker(SelectionKey key) {
            this.key = key;
        }

        public void addData(ByteBuffer newData, int len) {
            int newSize = bufSize + len;
            if (newSize > buffer.length) {
                realloc(newSize);
            }
            newData.get(buffer, bufSize, len);
            bufSize = newSize;
        }

        private void realloc(int newSize) {
            byte[] newBuffer = new byte[Math.max(buffer.length << 1, newSize)];
            System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
            buffer = newBuffer;
        }

        private byte[] getData() {
            return Arrays.copyOfRange(buffer, 0, bufSize);
        }

        public boolean read() {
            String data = stringData();
            return data.substring(data.length() - 4).equals("endl");
        }

        private String stringData() {
            return new String(getData());
        }

        @Override
        public void run() {
            String data = stringData();
            data = data.substring(0, data.length() - 4);
            InputProcessor inputProcessor = new InputProcessor(data);
            try {
                output = inputProcessor.getResponse();
                readyWokers.add(this);
            } catch (IOException e) {
                System.err.println("failed create json " + e.getMessage());
            }
        }

        public ByteBuffer getOutput() {
            return output;
        }
    }
}


   
