package fsdk.wifidirectproba;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class SocketServer{

    private ServerSocket serverSocket;
    private boolean running = false; // флаг для проверки, запущен ли сервер
    private boolean stopping = false;
    ArrayList<ClientManager> clientManagers = new ArrayList<>();
    private int timeSilence;

    SocketServer(OnEvents listenerSocket, int timeSilence){
        setListener(listenerSocket);
        this.timeSilence = timeSilence;
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (clientManagers.size() > 0) {

                    ArrayList<ClientManager> removingManagers = new ArrayList<>();
                    for (ClientManager manager : clientManagers){
                        if (manager.socket.isClosed()){
                            removingManagers.add(manager);
                        } else {
                            manager.incTimeSilence();
                        }
                    }
                    for(int i=0;i<removingManagers.size();i++){
                        clientManagers.remove(removingManagers.get(i));
                    }
                    removingManagers.clear();
                } else {
                    if(stopping){ closeServer();}
                }
            }
        }, 1, 1000);
    }

    interface OnEvents{
        void onServerRunning();
        void onServerStopped();
        void onNewConnect(Client client);
        void onDisconnect(Client client);
        void onDataReceived(Client client);
        void onDataSent(Client client);
        void onError(int errId, String msg);
    }

    private static OnEvents listenerSocket;
    private void setListener(OnEvents listener){listenerSocket = listener;}

    void runServer(final int port) {
        stopping = false;
        if(!running) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        serverSocket = new ServerSocket(port);
                        running = true;
                        listenerSocket.onServerRunning();
                        while (running) {
                            Socket clientSocket = serverSocket.accept();
                            ClientManager clientManager = new ClientManager(clientSocket, clientEvents, timeSilence);
                            clientManagers.add(clientManager);
                            listenerSocket.onNewConnect(clientManager.client);
                        }
                    }
                    catch (IOException ioe){
                        listenerSocket.onError(1,"\nThe acceptance mode was broken. "+ioe.getMessage());
                    }
                    catch (Exception e) {
                        listenerSocket.onError(2,"\nError starting server. " + e.getMessage());
                    }
                }
            }).start();
        } else {
            listenerSocket.onError(3,"\nError. Server already running.");
        }
    }

    private void closeServer(){
        try {
            serverSocket.close();
        } catch (IOException e) {
            listenerSocket.onError(4,"\nError closing server. "+e.getMessage());
        }
        serverSocket = null;
        running = false;
        stopping = false;
        listenerSocket.onServerStopped();
    }

    void stopServer(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if(running){
                    if (clientManagers.size() > 0){
                        stopping = true;
                        for (ClientManager manager : clientManagers) manager.closeConnect();
                    } else {
                        closeServer();
                    }
                }
            }
        }).start();
    }

    void disableClient(Client client){
        for(int i=0;i<clientManagers.size();i++){
            if(clientManagers.get(i).socket.getInetAddress().getHostAddress().equals(client.addressIP) &&
                    clientManagers.get(i).socket.getPort() == client.portIP){
               clientManagers.get(i).closeConnect();
               return;
            }
        }
    }

    private OnClientEvents clientEvents = new OnClientEvents() {
        @Override
        public void onSocketClosed(Client client) {
            for(int i=0;i<clientManagers.size();i++){
                if (clientManagers.get(i).client == client) {
                    listenerSocket.onDisconnect(client);
                    return;
                }
            }
        }

        @Override
        public void onDataReceived(Client client) {
            listenerSocket.onDataReceived(client);
        }

        @Override
        public void onDataSent(Client client) {
            listenerSocket.onDataSent(client);
        }
    };

    class Client{
        String addressIP;
        int portIP;
        byte[] buff;
        int count;

        Client (int rcvBuffSize){
           buff = new byte[rcvBuffSize];
        }
    }

    private interface OnClientEvents{
        void onSocketClosed(Client client);
        void onDataReceived(Client client);
        void onDataSent(Client client);
    }

    class ClientManager{
        private Client client;
        private Socket socket;
        private OnClientEvents clientEvents;
        private int timeCounter;
        private int timeSilence;

        ClientManager(final Socket socket, final OnClientEvents clientEvents, int timeSilence){
            this.socket = socket;
            this.clientEvents = clientEvents;
            this.timeSilence = timeSilence;

            client = new Client(256);
            client.addressIP = socket.getInetAddress().getHostAddress();
            client.portIP = socket.getPort();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!socket.isClosed()){
                        try {
                            int count = socket.getInputStream().read(client.buff);
                            if(count > 0){
                                timeCounter = 0;
                                client.count = count;
                                clientEvents.onDataReceived(client);
                            }
                        } catch (IOException e) {
                            listenerSocket.onError(5,"\nError receiving data. "+e.getMessage());
                        }
                    }
                    clientEvents.onSocketClosed(client);
                }
            }).start();
        }

        void sendData(final byte[] buff){
            final String ipAddress = socket.getInetAddress().getHostAddress();
            if(socket != null && socket.isClosed()){
                clientEvents.onSocketClosed(client);
            }
            if (socket == null || socket.isClosed()) {
                listenerSocket.onError(6,"\nError sending data to client IP:"+ipAddress+
                                        ". The socket was closed.");
                return;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        socket.getOutputStream().write(buff);
                        socket.getOutputStream().flush();
                        clientEvents.onDataSent(client);
                    } catch (IOException e) {
                        listenerSocket.onError(7,"\nError sending data to client IP:"+ipAddress+e.getMessage());
                    }
                }
            }).start();
        }

        void closeConnect(){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if(socket!=null){
                        try {
                            socket.close();
                        } catch (IOException e) {
                            listenerSocket.onError(8,"\nError closing the socket IP:"+client.addressIP+":"+client.portIP);
                        }
                    }
                }
            }).start();
        }

        @Override
        protected void finalize() throws Throwable
        {
            super.finalize();
            closeConnect();
        }

        void incTimeSilence(){
           timeCounter++;
           if(timeCounter >= timeSilence){
               timeCounter = 0;
               closeConnect();
           }
        }
    }
}
