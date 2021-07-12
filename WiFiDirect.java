package fsdk.wifidirectproba;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static android.os.Looper.getMainLooper;

class WiFiDirect {

    private static Channel channel;
    private static WifiP2pManager manager;
    private static WiFiDirectBroadcastReceiver receiver;
    private IntentFilter intentFilter;

    @SuppressLint("StaticFieldLeak")
    private static Context staticContext;

    private WiFiDirect(){
        intentFilter = new IntentFilter();
        // Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

    }

    @SuppressLint("StaticFieldLeak")
    private static WiFiDirect instance;

    static WiFiDirect getInstance(Context context){
        if(instance == null){
            instance = new WiFiDirect();
        }
        staticContext = context;
        manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        if (manager != null) {
            channel = manager.initialize(context, getMainLooper(), channelListener);
            receiver = new WiFiDirectBroadcastReceiver(manager,channel);
            receiver.setListener(onP2PEvent);
        }
        return instance;
    }

    private static WiFiDirectBroadcastReceiver.OnReceiverEvent onP2PEvent = new WiFiDirectBroadcastReceiver.OnReceiverEvent() {
        @Override
        public void onStateChangedActon(boolean status) {
            listenerEvent.onStateChangedActon(status);
        }

        @Override
        public void onPeersChangedActon(List<WifiP2pDevice> peers) {
            listenerEvent.onPeersChangedActon(peers);
        }

        @Override
        public void onConnectionChangedActon(boolean status) {
            listenerEvent.onConnectionChangedActon(status);
        }

        @Override
        public void onThisDeviceChangedActon(String device) {
            listenerEvent.onThisDeviceChangedActon(device);
        }

        @Override
        public void onConnectionAvailable(WifiP2pInfo info) {
            listenerEvent.onConnectionAvailable(info);
        }
    };

    private Context context;
    private static final int PERMISSION_REQUEST_CODE = 1;
    private boolean isRegistered = false;
    void registerReceiver(Activity activity){
        context = activity.getApplicationContext();
        int permissionStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            context.registerReceiver(receiver, intentFilter);
            isRegistered = true;
        } else {
            ActivityCompat.requestPermissions(activity,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    }, PERMISSION_REQUEST_CODE);
        }
    }

    void unregisterReceiver(){
        if(isRegistered){
            context.unregisterReceiver(receiver);
            isRegistered = false;
        }
    }

    void discoverPeers(){
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                listenerEvent.onInitPeers(true);
            }

            @Override
            public void onFailure(int reason) {
                listenerEvent.onInitPeers(false);
            }
        });
    }

    void stopDiscoverPeers(){
        manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                listenerEvent.onStopDiscoverPeers(true);
            }

            @Override
            public void onFailure(int reason) {
                listenerEvent.onStopDiscoverPeers(false);
            }
        });
    }

    void connectToDevice(String address){

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = address;
        config.wps.setup = WpsInfo.PBC;
        config.groupOwnerIntent = 0;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver notifies us. Ignore for now.
                listenerEvent.onConnectionToDevice(true);
            }

            @Override
            public void onFailure(int reason) {
                // Connect failed. Retry.
                listenerEvent.onConnectionToDevice(false);
            }
        });

    }

    void removeGroup(){
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                listenerEvent.onRemoveGroup(true);
            }

            @Override
            public void onFailure(int reason) {
                listenerEvent.onRemoveGroup(false);
            }
        });

    }

    void cancelConnect(){
        manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                listenerEvent.onCancelConnect(true);
            }

            @Override
            public void onFailure(int reason) {
                listenerEvent.onCancelConnect(false);
            }
        });
    }

    private static WifiP2pManager.ChannelListener channelListener = new WifiP2pManager.ChannelListener() {
        @Override
        public void onChannelDisconnected() {
            if(manager!=null) {
                listenerEvent.onChannelDisconnected(true);
                channel = manager.initialize(staticContext, getMainLooper(), this);
            } else {
                listenerEvent.onChannelDisconnected(false);
            }
        }
    };



    interface OnEvents{
        void onStateChangedActon(boolean status);
        void onPeersChangedActon(List<WifiP2pDevice> peers);
        void onConnectionChangedActon(boolean status);
        void onThisDeviceChangedActon(String device);
        /**
         String groupOwnerAddress = info.groupOwnerAddress.getHostAddress();
         if (info.groupFormed && info.isGroupOwner) {
            // Do whatever tasks are specific to the group owner.
            // One common case is creating a group owner thread and accepting
            // incoming connections.
         } else if (info.groupFormed) {
            // The other device acts as the peer (client). In this case,
            // you'll want to create a peer thread that connects
            // to the group owner.
         }
         */
        void onConnectionAvailable(WifiP2pInfo info);
        void onInitPeers(boolean status);
        void onStopDiscoverPeers(boolean status);
        void onConnectionToDevice(boolean status);

        /**
         *
         * @param status
         * if manager != null then the event status return true and initialisation the channel is retry
         */
        void onChannelDisconnected(boolean status);
        void onRemoveGroup(boolean status);
        void onCancelConnect(boolean status);
    }

    private static OnEvents listenerEvent;
    void setListener(OnEvents listener){listenerEvent = listener;}

    private static class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

        private final WifiP2pManager manager;
        private final Channel channel;
        //private NetworkRequest.Builder builder;

        public WiFiDirectBroadcastReceiver(final WifiP2pManager manager, final Channel channel) {
            super();
            this.manager = manager;
            this.channel = channel;
            //builder = new NetworkRequest.Builder();
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Check to see if Wi-Fi is enabled and notify appropriate activity
                // Determine if Wifi P2P mode is enabled or not, alert
                // the Activity.
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    listener.onStateChangedActon(true);
                } else {
                    listener.onStateChangedActon(false);
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // Call WifiP2pManager.requestPeers() to get a list of current peers
                if(manager != null){
                    manager.requestPeers(channel,peerListListener);
                }

            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                // Respond to new connection or disconnections
                if (manager == null) {
                    return;
                }

                NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

                if (networkInfo.isConnected()) {

                    // We are connected with the other device, request connection
                    // info to find group owner IP
                    listener.onConnectionChangedActon(true);
                    manager.requestConnectionInfo(channel, infoListener);
                } else {
                    listener.onConnectionChangedActon(false);
                }


            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                // Respond to this device's wifi state changing
                String device = (Objects.requireNonNull(intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE))).toString();
                listener.onThisDeviceChangedActon(device);
            }
        }

        private List<WifiP2pDevice> peers = new ArrayList<>();
        WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peerList) {
                Collection<WifiP2pDevice> refreshedPeers = peerList.getDeviceList();
                if (!refreshedPeers.equals(peers)) {
                    peers.clear();
                    peers.addAll(refreshedPeers);
                    listener.onPeersChangedActon(peers);
                }
                listener.onPeersChangedActon(peers);
            }
        };

        WifiP2pManager.ConnectionInfoListener infoListener = new WifiP2pManager.ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                listener.onConnectionAvailable(info);
            }
        };

        public interface OnReceiverEvent{
            void onStateChangedActon(boolean status);
            void onPeersChangedActon(List<WifiP2pDevice> peers);
            void onConnectionChangedActon(boolean status);
            void onThisDeviceChangedActon(String device);
            void onConnectionAvailable(WifiP2pInfo info);
        }

        private OnReceiverEvent listener;
        public void setListener(OnReceiverEvent listener){this.listener = listener;}
    }
}
