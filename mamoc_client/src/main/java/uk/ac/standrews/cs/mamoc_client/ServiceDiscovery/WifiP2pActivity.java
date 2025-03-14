package uk.ac.standrews.cs.mamoc_client.ServiceDiscovery;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import uk.ac.standrews.cs.mamoc_client.DB.DBAdapter;
import uk.ac.standrews.cs.mamoc_client.MamocFramework;
import uk.ac.standrews.cs.mamoc_client.Model.MobileNode;
import uk.ac.standrews.cs.mamoc_client.R;
import uk.ac.standrews.cs.mamoc_client.Utils.Utils;
import uk.ac.standrews.cs.mamoc_client.Utils.NotificationToast;


public class WifiP2pActivity extends AppCompatActivity implements PeerListFragment.OnListFragmentInteractionListener
        , WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener {

    public static final String FIRST_DEVICE_CONNECTED = "first_device_connected";
    public static final String KEY_FIRST_DEVICE_IP = "first_device_ip";

    private static final String WRITE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final int WRITE_PERM_REQ_CODE = 19;

    PeerListFragment deviceListFragment;
    View progressBar;

    WifiP2pManager wifiP2pManager;
    WifiP2pManager.Channel wifip2pChannel;
    WiFiDirectBroadcastReceiver wiFiDirectBroadcastReceiver;
    private boolean isWifiP2pEnabled = false;

    private boolean isWDConnected = false;

    private MamocFramework mamocFramework;

    ServiceDiscovery serviceDiscovery;

//    private ConnectionListener connListener;

    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.nsd_activity);

        // add back arrow to toolbar
        if (getSupportActionBar() != null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        initialize();
    }

    private void initialize() {

        progressBar = findViewById(R.id.progressBarNSD);

        String myIP = Utils.getWiFiIPAddress(this);
        Utils.save(this, TransferConstants.KEY_MY_IP, myIP);

//        Starting connection listener with default for now
//        connListener = new ConnectionListener(LocalDashWiFiDirect.this, TransferConstants.INITIAL_DEFAULT_PORT);
//        connListener.start();

        setToolBarTitle(0);

        wifiP2pManager = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        wifip2pChannel = wifiP2pManager.initialize(this, getMainLooper(), null);

        // Starting connection listener with default port for now
//        mamocFramework = (MamocFramework) getApplicationContext();
        serviceDiscovery.startConnectionListener(TransferConstants.INITIAL_DEFAULT_PORT);

        checkWritePermission();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_nsd, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void findPeers(View v) {

        if (!isWDConnected) {
            Snackbar.make(v, "Finding peers", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            wifiP2pManager.discoverPeers(wifip2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    NotificationToast.showToast(WifiP2pActivity.this, "Peer discovery started");
                }

                @Override
                public void onFailure(int reasonCode) {
                    NotificationToast.showToast(WifiP2pActivity.this, "Peer discovery failure: "
                            + reasonCode);
                }
            });
        }
    }

    @Override
    protected void onPause() {
//        if (mNsdHelper != null) {
//            mNsdHelper.stopDiscovery();
//        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localDashReceiver);
        unregisterReceiver(wiFiDirectBroadcastReceiver);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter localFilter = new IntentFilter();
        localFilter.addAction(DataHandler.DEVICE_LIST_CHANGED);
        localFilter.addAction(FIRST_DEVICE_CONNECTED);
        localFilter.addAction(DataHandler.REQUEST_RECEIVED);
        localFilter.addAction(DataHandler.RESPONSE_RECEIVED);
        LocalBroadcastManager.getInstance(WifiP2pActivity.this).registerReceiver(localDashReceiver,
                localFilter);

        IntentFilter wifip2pFilter = new IntentFilter();
        wifip2pFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        wifip2pFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        wifip2pFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        wifip2pFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        wiFiDirectBroadcastReceiver = new WiFiDirectBroadcastReceiver(wifiP2pManager,
                wifip2pChannel, this);
        registerReceiver(wiFiDirectBroadcastReceiver, wifip2pFilter);

        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(DataHandler.DEVICE_LIST_CHANGED));
    }

    @Override
    protected void onDestroy() {
//        mNsdHelper.tearDown();
//        connListener.tearDown();
        serviceDiscovery.stopConnectionListener();
        Utils.clearPreferences(WifiP2pActivity.this);
//        Utils.deletePersistentGroups(wifiP2pManager, wifip2pChannel);
//        DBAdapter.getInstance(WifiP2pActivity.this).clearDatabase();
        wifiP2pManager.removeGroup(wifip2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {

            }

            @Override
            public void onFailure(int i) {

            }
        });

        super.onDestroy();
    }

    private BroadcastReceiver localDashReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case FIRST_DEVICE_CONNECTED:
//                    connListener.tearDown();
//                    int newPort = ConnectionUtils.getPort(LocalDashWiFiDirect.this);
//                    connListener = new ConnectionListener(LocalDashWiFiDirect.this,
//                            newPort);
//                    connListener.start();
//                    appController.stopConnectionListener();
//                    appController.startConnectionListener(ConnectionUtils.getPort(LocalDashWiFiDirect.this));
                    serviceDiscovery.restartConnectionListenerWith(Utils.getPort(WifiP2pActivity.this));

                    String senderIP = intent.getStringExtra(KEY_FIRST_DEVICE_IP);
                    int port = DBAdapter.getInstance(WifiP2pActivity.this).getMobileDevice(senderIP).getPort();
                    DataSender.sendCurrentDeviceData(WifiP2pActivity.this, senderIP, port, true);
                    isWDConnected = true;
                    break;
                case DataHandler.DEVICE_LIST_CHANGED:
                    ArrayList<MobileNode> devices = DBAdapter.getInstance(WifiP2pActivity.this)
                            .getMobileDevicesList();
                    int peerCount = (devices == null) ? 0 : devices.size();
                    if (peerCount > 0) {
                        progressBar.setVisibility(View.GONE);
                        deviceListFragment = new PeerListFragment();
                        Bundle args = new Bundle();
                        args.putSerializable(PeerListFragment.ARG_DEVICE_LIST, devices);
                        deviceListFragment.setArguments(args);

                        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                        ft.replace(R.id.deviceListHolder, deviceListFragment);
                        ft.setTransition(FragmentTransaction.TRANSIT_NONE);
                        ft.commit();
                    }
                    setToolBarTitle(peerCount);
                    break;
                case DataHandler.REQUEST_RECEIVED:
                     MobileNode chatRequesterDevice = (MobileNode) intent.getSerializableExtra(DataHandler
                            .KEY_IS_REQUEST_ACCEPTED);
//                    Utils.getChatRequestDialog(WifiP2pActivity.this,
//                            chatRequesterDevice).show();
                    break;
                case DataHandler.RESPONSE_RECEIVED:
                    boolean isChatRequestAccepted = intent.getBooleanExtra(DataHandler.KEY_IS_REQUEST_ACCEPTED, false);
                    if (!isChatRequestAccepted) {
                        NotificationToast.showToast(WifiP2pActivity.this, "Chat request " +
                                "rejected");
                    } else {
                        MobileNode chatDevice = (MobileNode) intent.getSerializableExtra(DataHandler
                                .KEY_REQUEST);
//                        DialogUtils.openChatActivity(WifiP2pActivity.this, chatDevice);
                        NotificationToast.showToast(WifiP2pActivity.this, chatDevice
                                .getDeviceID() + "Accepted Chat request");
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private MobileNode selectedDevice;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
//            case DialogUtils.CODE_PICK_IMAGE:
//                if (resultCode == RESULT_OK) {
//                    Uri imageUri = data.getData();
//                    DataSender.sendFile(WifiP2pActivity.this, selectedDevice.getIp(),
//                            selectedDevice.getPort(), imageUri);
//                }
//                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            finish();
        }
    }

    private void checkWritePermission() {
        boolean isGranted = Utils.checkPermission(WRITE_PERMISSION, this);
        if (!isGranted) {
            Utils.requestPermission(WRITE_PERMISSION, WRITE_PERM_REQ_CODE, this);
        }
    }

    boolean isConnectionInfoSent = false;

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {

        if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner && !isConnectionInfoSent) {

            isWDConnected = true;

//            connListener.tearDown();
//            connListener = new ConnectionListener(LocalDashWiFiDirect.this, ConnectionUtils.getPort
//                    (LocalDashWiFiDirect.this));
//            connListener.start();
//            appController.stopConnectionListener();
//            appController.startConnectionListener(ConnectionUtils.getPort(LocalDashWiFiDirect.this));
            serviceDiscovery.restartConnectionListenerWith(Utils.getPort(WifiP2pActivity.this));

            String groupOwnerAddress = wifiP2pInfo.groupOwnerAddress.getHostAddress();
            DataSender.sendCurrentDeviceData(WifiP2pActivity.this, groupOwnerAddress, TransferConstants
                    .INITIAL_DEFAULT_PORT, true);
            isConnectionInfoSent = true;
        }
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {

        ArrayList<MobileNode> deviceDTOs = new ArrayList<>();

        List<WifiP2pDevice> devices = (new ArrayList<>());
        devices.addAll(peerList.getDeviceList());
        for (WifiP2pDevice device : devices) {
            MobileNode deviceDTO = new MobileNode(this);
            deviceDTO.setIp(device.deviceAddress);
            deviceDTO.setNodeName(device.deviceName);
            deviceDTO.setBatteryLevel(mamocFramework.getSelfNode().getBatteryLevel());
            deviceDTO.setPort(-1);
            deviceDTOs.add(deviceDTO);
        }


        progressBar.setVisibility(View.GONE);
        deviceListFragment = new PeerListFragment();
        Bundle args = new Bundle();
        args.putSerializable(PeerListFragment.ARG_DEVICE_LIST, deviceDTOs);
        deviceListFragment.setArguments(args);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.deviceListHolder, deviceListFragment);
        ft.setTransition(FragmentTransaction.TRANSIT_NONE);
        ft.commit();
    }

    @Override
    public void onListFragmentInteraction(MobileNode deviceDTO) {
        if (!isWDConnected) {
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = deviceDTO.getIp();
            config.wps.setup = WpsInfo.PBC;
            config.groupOwnerIntent = 4;
            wifiP2pManager.connect(wifip2pChannel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    // Connection request succeeded. No code needed here
                }

                @Override
                public void onFailure(int reasonCode) {
                    NotificationToast.showToast(WifiP2pActivity.this, "Connection failed. try" +
                            " again: reason: " + reasonCode);
                }
            });
        } else {
            selectedDevice = deviceDTO;
//            showServiceSelectionDialog();
//            Utils.getServiceSelectionDialog(WifiP2pActivity.this, deviceDTO).show();
        }
    }

    private void setToolBarTitle(int peerCount) {
        if (getSupportActionBar() != null) {
            String title = String.format(getString(R.string.nsd_title_with_count), String
                    .valueOf(peerCount));
            getSupportActionBar().setTitle(title);

        }
    }
}

//    private void showChatRequestedDialog(final DeviceDTO device) {
//        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
//
//        String chatRequestTitle = getString(R.string.chat_request_title);
//        chatRequestTitle = String.format(chatRequestTitle, device.getPlayerName() + "(" + device
//                .getDeviceName() + ")");
//        alertDialog.setTitle(chatRequestTitle);
//        String[] types = {"Accept", "Reject"};
//        alertDialog.setItems(types, new DialogInterface.OnClickListener() {
//
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//
//                dialog.dismiss();
//                switch (which) {
//                    //Request accepted
//                    case 0:
//                        DialogUtils.openChatActivity(LocalDashWiFiDirect.this, device);
//                        NotificationToast.showToast(LocalDashWiFiDirect.this, "Chat request " +
//                                "accepted");
//                        DataSender.sendChatResponse(LocalDashWiFiDirect.this, device.getIp(),
//                                device.getPort(), true);
//                        break;
//                    // Request rejected
//                    case 1:
//                        DataSender.sendChatResponse(LocalDashWiFiDirect.this, device.getIp(),
//                                device.getPort(), false);
//                        NotificationToast.showToast(LocalDashWiFiDirect.this, "Chat request " +
//                                "rejected");
//                        break;
//                }
//            }
//
//        });
//
//        alertDialog.show();
//    }

//    private void openChatActivity(DeviceDTO chatDevice) {
//        Intent chatIntent = new Intent(LocalDashWiFiDirect.this, ChatActivity
//                .class);
//        chatIntent.putExtra(ChatActivity.KEY_CHAT_IP, chatDevice.getIp());
//        chatIntent.putExtra(ChatActivity.KEY_CHAT_PORT, chatDevice.getPort());
//        chatIntent.putExtra(ChatActivity.KEY_CHATTING_WITH, chatDevice.getPlayerName());
//        startActivity(chatIntent);
//    }

//    private void showServiceSelectionDialog() {
//        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
//        alertDialog.setTitle(selectedDevice.getDeviceName());
//        String[] types = {"Share image", "Chat"};
//        alertDialog.setItems(types, new DialogInterface.OnClickListener() {
//
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//
//                dialog.dismiss();
//                switch (which) {
//                    case 0:
//                        Intent imagePicker = new Intent(Intent.ACTION_PICK);
//                        imagePicker.setType("image/*");
//                        startActivityForResult(imagePicker, DialogUtils.CODE_PICK_IMAGE);
//                        break;
//                    case 1:
//                        DataSender.sendChatRequest(LocalDashWiFiDirect.this, selectedDevice.getIp
//                                (), selectedDevice.getPort());
//                        NotificationToast.showToast(LocalDashWiFiDirect.this, "chat request " +
//                                "sent");
//                        break;
//                }
//            }
//
//        });
//
//        alertDialog.show();
//    }