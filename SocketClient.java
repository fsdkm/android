package fsdk.wifidirectproba;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

public class SocketClient {
    private Socket socket;

    interface OnEvents{
        void onConnectOpen();
        void onConnectClose();
        void onDataReceived(byte[] buff,int len);
        void onDataSent();
        void onError(int errId, String msg);
    }

    private static OnEvents listenerSocket;

    SocketClient(OnEvents listenerSocket){
        SocketClient.listenerSocket = listenerSocket;
    }

    void openConnection(final String host, final int port){
        socket = new Socket();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket.bind(null);
                    socket.connect((new InetSocketAddress(host, port)),5000);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            byte[] data = new byte[4096];
                            while (!socket.isClosed()){
                                try {
                                    int count = socket.getInputStream().read(data);
                                    if(count > 0){
                                        listenerSocket.onDataReceived(data,count);
                                    }
                                } catch (IOException e) {
                                    listenerSocket.onError(1,"\nError receiving data. "+e.getMessage());
                                }
                            }
                        }
                    }).start();
                    listenerSocket.onConnectOpen();
                } catch (Exception e) {
                    listenerSocket.onError(2,"\nError connection. "+e.getMessage());
                    socket = null;
                }
            }
        }).start();
    }

    void closeConnection(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.close();
                        listenerSocket.onConnectClose();
                    }
                    catch (SocketException se){listenerSocket.onError(3,se.getMessage());}
                    catch (IOException e) {
                        listenerSocket.onError(4,"\nError closing connect. " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    void sendData(final byte[] buff){
        if (socket == null || socket.isClosed()) {
            listenerSocket.onError(5,"\nError sending data. The socket was closed or not created.");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket.getOutputStream().write(buff);
                    socket.getOutputStream().flush();
                    listenerSocket.onDataSent();
                } catch (IOException e) {
                    listenerSocket.onError(6,"\nError sending data. "+e.getMessage());
                }
            }
        }).start();
    }


    @Override
    protected void finalize() throws Throwable
    {
        super.finalize();
        closeConnection();
    }
}
