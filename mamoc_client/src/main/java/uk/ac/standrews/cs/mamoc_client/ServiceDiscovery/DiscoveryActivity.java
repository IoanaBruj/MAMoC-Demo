package uk.ac.standrews.cs.mamoc_client.ServiceDiscovery;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;

import uk.ac.standrews.cs.mamoc_client.Constants;
import uk.ac.standrews.cs.mamoc_client.MamocFramework;
import uk.ac.standrews.cs.mamoc_client.R;
import uk.ac.standrews.cs.mamoc_client.Utils.Utils;

import static uk.ac.standrews.cs.mamoc_client.Constants.CLOUD_IP;
import static uk.ac.standrews.cs.mamoc_client.Constants.REQUEST_CODE_ASK_PERMISSIONS;
import static uk.ac.standrews.cs.mamoc_client.Constants.EDGE_IP;
import static uk.ac.standrews.cs.mamoc_client.Constants.SERVICE_DISCOVERY_BROADCASTER;

public class DiscoveryActivity extends AppCompatActivity {

    private final String TAG = "DiscoveryActivity";
    private MamocFramework framework;

    private Button discoverButton, edgeBtn, cloudBtn;

    private TextView listeningPort, edgeTextView, cloudTextView;
    private final IntentFilter intentFilter = new IntentFilter();
    WifiP2pManager.Channel channel;
    WifiP2pManager manager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discovery);

        Toolbar toolbar = findViewById(R.id.toolbarDiscovery);
        setSupportActionBar(toolbar);

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        LocalBroadcastManager.getInstance(this).registerReceiver(serviceDiscoveryReceiver,
                new IntentFilter(SERVICE_DISCOVERY_BROADCASTER));

        // add back arrow to toolbar
        if (getSupportActionBar() != null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        framework = MamocFramework.getInstance(this);
        framework.serviceDiscovery = ServiceDiscovery.getInstance(this);
        framework.serviceDiscovery.startConnectionListener();

        listeningPort = findViewById(R.id.ListenPort);

        discoverButton = findViewById(R.id.discoverBtn);
        discoverButton.setOnClickListener(View -> startWiFiP2PActivity());

        edgeBtn = findViewById(R.id.edgeConnect);
        edgeTextView = findViewById(R.id.edgeTextView);

        cloudBtn = findViewById(R.id.cloudConnect);
        cloudTextView = findViewById(R.id.cloudTextView);

        edgeBtn.setOnClickListener(view -> framework.serviceDiscovery.connectEdge(edgeTextView.getText().toString()));
        cloudBtn.setOnClickListener(view -> framework.serviceDiscovery.connectCloud(cloudTextView.getText().toString()));

        checkWritePermissions();
        logInterfaces();
        loadPrefs();
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
    }

    private void loadPrefs() {
//        String enteredEdgeIP = Utils.getValue(this, "edgeIP");
//        if (enteredEdgeIP != null) {
//            edgeTextView.setText(enteredEdgeIP);
//        } else {
            edgeTextView.setText(EDGE_IP);
//        }

        String enteredCloudIP = Utils.getValue(this, "cloudIP");
        if (enteredCloudIP != null) {
            cloudTextView.setText(enteredCloudIP);
        } else {
            cloudTextView.setText(CLOUD_IP);
        }
    }

    private void edgeConnected() {
        Utils.alert(DiscoveryActivity.this, "Connected.");
        edgeBtn.setText("Status: Connected to " + EDGE_IP);
        edgeBtn.setEnabled(false);
    }

    private void edgeDisconnected(){
        Utils.alert(DiscoveryActivity.this, "Left.");
        edgeBtn.setEnabled(true);
    }

    private void cloudConnected() {
        Utils.alert(DiscoveryActivity.this, "Connected.");
        cloudBtn.setText("Status: Connected to " + CLOUD_IP);
        cloudBtn.setEnabled(false);
    }

    private void cloudDisconnected(){
        Utils.alert(DiscoveryActivity.this, "Left.");
        cloudBtn.setEnabled(true);
    }

    private BroadcastReceiver serviceDiscoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received Intent: " + intent.getAction());

            switch (Objects.requireNonNull(intent.getAction())){
                case "connected":
                    if (intent.getStringExtra("node").equals("edge"))
                        edgeConnected();
                    else if (intent.getStringExtra("node").equals("cloud"))
                        cloudConnected();
                    break;
                case "disconnected":
                    if (intent.getStringExtra("node").equals("edge"))
                        edgeDisconnected();
                    else if (intent.getStringExtra("node").equals("cloud"))
                        cloudDisconnected();
                    break;
            }
            String message = intent.getStringExtra("message");
            Log.d("receiver", "Got message: " + message);
        }
    };

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceDiscoveryReceiver);
        super.onDestroy();
    }

    @SuppressLint({"StringFormatMatches", "SetTextI18n"})
    @Override
    protected void onResume() {
        super.onResume();
        listeningPort.setText(String.format(getString(R.string.port_info), Utils.getPort(this)));

        if (framework.serviceDiscovery.isEdgeConnected()){
            edgeBtn.setText("Status: Connected to " + EDGE_IP);
            edgeBtn.setEnabled(false);
        } else{
            edgeBtn.setEnabled(true);
        }

        if (framework.serviceDiscovery.isCloudConnected()){
            cloudBtn.setText("Status: Connected to " + CLOUD_IP);
            cloudBtn.setEnabled(false);
        } else{
            cloudBtn.setEnabled(true);
        }
    }

    private void checkWritePermissions() {

        boolean isGranted = Utils.checkPermission(Constants.WRITE_PERMISSION, this);
        if (!isGranted) {
            Utils.requestPermission(Constants.WRITE_PERMISSION, Constants
                    .WRITE_PERM_REQ_CODE, this);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) ||
                    (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS) != PackageManager.PERMISSION_GRANTED) ||
                    (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            }
        } else {
            if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_ASK_PERMISSIONS) {
            final int numOfRequest = grantResults.length;
            final boolean isGranted = numOfRequest == 1
                    && PackageManager.PERMISSION_GRANTED == grantResults[numOfRequest - 1];
            if (isGranted) {
                // you are good to go
            } else {
                Toast.makeText(this, "Please allow all the needed permissions", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void logInterfaces(){
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            for (NetworkInterface ni: Collections.list(interfaces)
                 ) {
                // Only display the up and running network interfaces
                if (ni.isUp()) {
                    Log.v(TAG, "Display name: " + ni.getDisplayName());
                    Log.v(TAG, "name: " + ni.getName());
                    Enumeration<InetAddress> addresses = ni.getInetAddresses();
                    for (InetAddress singleNI : Collections.list(addresses)
                            ) {
                        Log.v(TAG, "inet address: " + singleNI.getHostAddress());
                    }
                }

            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private void startWiFiP2PActivity(){

        // For WiFiP2P it shouldn't matter

    //    if (Utils.isWifiConnected(this)){
            Intent nsdIntent = new Intent(DiscoveryActivity.this, WiFiP2PSDActivity.class);
            startActivity(nsdIntent);
            finish();
//        } else {
//            Toast.makeText(this, "Wifi not connected! :(", Toast.LENGTH_SHORT).show();
//        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
