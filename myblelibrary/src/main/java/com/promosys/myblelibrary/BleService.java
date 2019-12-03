package com.promosys.myblelibrary;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BleService {

    public static BluetoothManager btManager;
    public static BluetoothAdapter btAdapter;
    public static BluetoothLeScanner btScanner;

    static Boolean btScanning = false;
    static int deviceIndex = 0;
    static ArrayList<BluetoothDevice> devicesDiscovered = new ArrayList<BluetoothDevice>();
    static BluetoothGatt bluetoothGatt;
    static BluetoothGattCharacteristic sendCharacteristic;
    static private String strDevice = "";

    static char ETX = (char)0x03;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public Map<String, String> uuids = new HashMap<String, String>();

    private static String CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = "";
    private static String CHARACTERISTIC_WRITE_UUID = "";
    private static String SERVICE_WRITE_UUID = "";

    public static String SCANNED_MAC_ADDRESS = "";

    // Stops scanning after 5 seconds.
    private static Handler mHandler = new Handler();
    private static final long SCAN_PERIOD = 5000;

    // This is the object that receives interactions from clients.
    //private final IBinder mBinder = new LocalBinder();
    private static StringBuffer strBleBuffer = new StringBuffer();

    public static boolean isWaitingReply = false;
    public static boolean isSendingPartData = false;
    public static boolean isWaitingBleReply = false;

    public static boolean isFirstConnected = true;

    public static boolean isScanForNearbyDevice = false;

    private static boolean isBluetoothConnected = false;

    private static int intStringLength = 0;
    private static int intAllowedDataEnd = 15;

    private static String mBlefirstUid = "";
    private static String mBlesecondUid = "";

    public static final String mBroadcastBleOff = "promosys.com.myblelibrary.bleoff";
    public static final String mBroadcastBleDisabled = "promosys.com.myblelibrary.bledisabled";
    public static final String mBroadcastBleConnected = "promosys.com.myblelibrary.bleconnected";
    public static final String mBroadcastBleDisconnected = "promosys.com.myblelibrary.bledisconnected";
    public static final String mBroadcastBleGotReply = "promosys.com.myblelibrary.blegotreply";
    public static final String mBroadcastBleDeviceNotFound = "promosys.com.myblelibrary.blenotfound";
    public static final String mBroadcastConnectionEstablished = "promosys.com.myblelibrary.bleestablished";
    public static final String mBroadcastFailedCharacteristics = "promosys.com.myblelibrary.failedcharacteristics";
    public static final String mBroadcastCheckAlive = "promosys.com.myblelibrary.checkalive";

    public static boolean mBleisNewVersion = false;
    public static Context mBleContext;

    public static void initBle(Context bleContext, boolean isProjectNew, String firstUid, String secondUid){
        mBlefirstUid = firstUid;
        mBlesecondUid = secondUid;
        mBleContext = bleContext;
        mBleisNewVersion = isProjectNew;
        btManager = (BluetoothManager) bleContext.getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btScanner = btAdapter.getBluetoothLeScanner();
        }

        if (btAdapter != null && !btAdapter.isEnabled()) {
            Log.i("BleService","Bluetooth is off");
            sendToMainActivity(mBroadcastBleDisabled,"","");
        }
    }

    // Device scan callback.
    private static BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            try {
                if(device.getName() != null){
                    Log.i("MyBleService","deviceName: " + device.getName());
                    Log.i("MyBleService","deviceName: " + device.getAddress());
                }

                //Filter device with broadcast name contains certain characters
                if(isScanForNearbyDevice){
                    if(device.getName().substring(0,2).contains(mBlefirstUid)){
                        if(device.getName().substring(2,4).contains(mBlesecondUid)){
                            SCANNED_MAC_ADDRESS = device.getName();
                            startConnecting(device);
                        }
                    }

                }else {
                    if(device.getName().equals(SCANNED_MAC_ADDRESS)){
                        startConnecting(device);
                    }
                }
            }catch (NullPointerException error){
                //Log.i("MainActivity",error.toString());
            }
        }
    };

    public static void startConnecting(BluetoothDevice device){
        strDevice = device.getName();
        devicesDiscovered.add(device);
        deviceIndex++;

        btScanning = false;
        stopScanning();
        Log.i("MyBleService","devicesDiscovered: " + devicesDiscovered.size());

        if(devicesDiscovered.size() == 1){
            Log.i("MyBleService","Connecting");
            //connectToDeviceSelected(0);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    connectToDeviceSelected(0);
                }
            }, 1000);

        }
    }

    public static void startScanning() {
        System.out.println("start scanning");
        btScanning = true;
        deviceIndex = 0;
        devicesDiscovered.clear();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    //btScanner.startScan(leScanCallback);
                    btAdapter.startLeScan(mLeScanCallback);
                }
            }
        });

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(btScanning){
                    Toast.makeText(mBleContext,"Device not found",Toast.LENGTH_SHORT).show();
                    sendToMainActivity(mBroadcastBleDeviceNotFound,"","");
                    stopScanning();
                }
            }
        }, SCAN_PERIOD);
    }

    public static void stopScanning() {
        System.out.println("stopping scanning");
        btScanning = false;
        isScanForNearbyDevice = false;
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    //btScanner.stopScan(leScanCallback);
                    btAdapter.stopLeScan(mLeScanCallback);
                }
            }
        });
    }

    public static void connectToDeviceSelected(int deviceIndex) {
        int deviceSelected = deviceIndex;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = devicesDiscovered.get(deviceSelected).connectGatt(mBleContext, true, btleGattCallback,BluetoothDevice.TRANSPORT_LE);
        }else {
            bluetoothGatt = devicesDiscovered.get(deviceSelected).connectGatt(mBleContext, true, btleGattCallback);
        }
        //bluetoothGatt = devicesDiscovered.get(deviceSelected).connectGatt(mBleContext, false, btleGattCallback);
    }

    public static void disconnectDeviceSelected() {
        if(bluetoothGatt != null)
        {
            bluetoothGatt.disconnect();
        }
    }

    private static void sendToMainActivity(String whichBroadcast,String extra,String extraKey){
        Intent sendMainActivity = new Intent();
        sendMainActivity.setAction(whichBroadcast);
        if(!(extra.isEmpty())){
            sendMainActivity.putExtra(extraKey,extra);
        }
        mBleContext.sendBroadcast(sendMainActivity);

    }

    // Device connect call back
    private static final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            sendCharacteristic = characteristic;
            super.onCharacteristicChanged(gatt, characteristic);
            byte[] messageBytes = characteristic.getValue();
            String messageString = null;
            try {
                messageString = new String(messageBytes, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.e("MainActivity", "Unable to convert message bytes to string");
            }

            /*
            if (messageString != null){
                strBleBuffer.append(messageString);
                final String strSubMessage = strBleBuffer.toString();
                strBleBuffer.delete(0,strBleBuffer.length()-1);
                sendToMainActivity(mBroadcastBleGotReply,messageString,"bleMessage");
            }
             */

            if(mBleisNewVersion){
                isWaitingBleReply = false;
                isWaitingReply = false;
                sendToMainActivity(mBroadcastBleGotReply,messageString,"bleMessage");
            }else {
                //check whether the message is from the correct ble device
                if (messageString.contains(String.valueOf(ETX))){
                    isWaitingBleReply = false;
                    isWaitingReply = false;
                    strBleBuffer.append(messageString);
                    final String strSubMessage = strBleBuffer.toString();
                    strBleBuffer.delete(0,strBleBuffer.length()-1);
                    sendToMainActivity(mBroadcastBleGotReply,strSubMessage,"bleMessage");
                }else {
                    strBleBuffer.append(messageString);
                }
            }
        }

        //To request MTU value (byte length for data transfer)
        @Override
        public void onMtuChanged(final BluetoothGatt gatt, final int mtu, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("MainActivity", "MTU changed to: " + mtu);
                intStringLength = mtu;
                bluetoothGatt.discoverServices();
            } else {
                Log.i("MainActivity", "onMtuChanged error: " + status + ", mtu: " + mtu);
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        //Bluetooth Connection status
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            switch (newState) {
                //Ble Disconnected
                case 0:
                    isBluetoothConnected = false;

                    isSendingPartData = false;
                    isWaitingBleReply = false;

                    sendToMainActivity(mBroadcastBleDisconnected,"","");
                    bluetoothGatt.close();

                    break;

                //Ble Connected
                case 2:
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    gatt.requestMtu(512);

                    isBluetoothConnected = true;

                    break;
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            displayGattServices(bluetoothGatt.getServices());
        }

        // Reading data from bluetooth device
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.i("MainActivity","onCharRead: " + characteristic);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        // Writing data to bluetooth device
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if(isSendingPartData){
                    isWaitingBleReply = false;
                }
            }
            super.onCharacteristicWrite(gatt, characteristic, status);
        }
    };

    // Display all services available for ble
    private static void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            final String uuid = gattService.getUuid().toString();
            Log.i("MainActivity","Service discovered: " + uuid);

            new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                final String charUuid = gattCharacteristic.getUuid().toString();

                //Automatically set characteristics for writing
                if(gattCharacteristic.getProperties() == 12){
                    CHARACTERISTIC_WRITE_UUID = charUuid;
                    SERVICE_WRITE_UUID = uuid;
                }

                //Automatically set characteristics for receiving data
                if(gattCharacteristic.getProperties() == 16){
                    for (BluetoothGattDescriptor descriptor:gattCharacteristic.getDescriptors()){
                        CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = descriptor.getUuid().toString();
                    }
                    setCharacteristicNotification(gattService.getUuid(),gattCharacteristic.getUuid(),true);
                }
            }
        }

    }

    //Set the notify characteristics
    public static void setCharacteristicNotification(UUID serviceUuid, UUID characteristicUuid,
                                              boolean enable) {
        BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(serviceUuid).getCharacteristic(characteristicUuid);
        bluetoothGatt.setCharacteristicNotification(characteristic, enable);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(descriptor);

        //once all characteristics are set, notify the MainActivity that bluetooth is connected and can proceed with data exchange
        sendToMainActivity(mBroadcastBleConnected,"","");
    }

    // Cut the string to smaller pieces before transmitting (some bluetooth device can accept maximum 20 bytes per transfer)
    public static void sendLongString(String sendString){
        Log.i("BleService","sendLongString: " + sendString);
        if(isBluetoothConnected){
            if(!isWaitingReply){
                isWaitingReply = true;
                if(intStringLength > sendString.length()){
                    writeCustomCharacteristic(sendString);
                }else {
                    isSendingPartData = true;
                    int data_begin = 0;
                    int data_end = 15;

                    while (isSendingPartData){
                        if(!isWaitingBleReply){
                            if(data_end == sendString.length()){
                                String sendData = sendString.substring(data_begin,data_end)+ "\r\n";
                                writeCustomCharacteristic(sendData);
                                isSendingPartData = false;
                            }else {
                                isWaitingBleReply = true;
                                writeCustomCharacteristic(sendString.substring(data_begin,data_end));
                            }
                            data_begin = data_end;
                            data_end = data_end + intAllowedDataEnd;
                            if (data_end > sendString.length()){
                                data_end = sendString.length();
                            }
                        }
                    }

                }

            }
        }
    }

    //For firmware update
    public static void uploadingBin2(String sendStr){
        if(!isWaitingReply){
            isWaitingReply = true;

            if(intStringLength>sendStr.length()){
                writeCustomCharacteristic(sendStr);
                Log.i("MyBleService","sendLongString: " + sendStr);
            }else {
                if(intStringLength == 247){
                    intAllowedDataEnd = 150;
                }else {
                    intAllowedDataEnd = 15;
                }
                Log.i("MyBleService","intAllowedDataEnd: " + intAllowedDataEnd);
                isSendingPartData = true;
                int data_begin = 0;
                int data_end = intAllowedDataEnd;
                while (isSendingPartData){
                    if(!isWaitingBleReply){
                        if(data_end == sendStr.length()){
                            String sendData = sendStr.substring(data_begin,data_end) + "\r\n";
                            writeCustomCharacteristic(sendData);
                            Log.i("MyBleService","sendData: " + sendData);
                            isSendingPartData = false;
                        }else {
                            isWaitingBleReply = true;
                            writeCustomCharacteristic(sendStr.substring(data_begin,data_end));
                            Log.i("MyBleService","sendData: " + sendStr.substring(data_begin,data_end));
                        }
                        data_begin = data_end;
                        data_end = data_end + intAllowedDataEnd;
                        //data_end = data_end + 50;
                        if (data_end > sendStr.length()){
                            data_end = sendStr.length();
                        }
                    }
                }
            }

        }
    }

    //Write String to bluetooth device
    public static void writeCustomCharacteristic(String message) {
        if(isBluetoothConnected){
            if (btAdapter == null || bluetoothGatt == null) {
                Log.i("MainActivity", "BluetoothAdapter not initialized");
                return;
            }

            BluetoothGattService mCustomService = bluetoothGatt.getService(UUID.fromString(SERVICE_WRITE_UUID));
            if(mCustomService == null){
                Log.w("MainActivity", "Custom BLE Service not found");
                return;
            }

            String originalString = message;
            byte[] b = new byte[message.length()];
            try {
                b = originalString.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            BluetoothGattCharacteristic mWriteCharacteristic = mCustomService.getCharacteristic(UUID.fromString(CHARACTERISTIC_WRITE_UUID));
            mWriteCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            mWriteCharacteristic.setValue(b);
            if(!bluetoothGatt.writeCharacteristic(mWriteCharacteristic)){
                Log.i("MainActivity", "Failed to write characteristic");
            }
        }

    }


    //To transfer data to MainActivity
    private static void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        Log.i("BleService","got data available");
        try {
            if(characteristic.getValue()!= null){
                byte[] bytes = characteristic.getValue();
                String str = new String(bytes, "UTF-8");
                Log.i("BleService","getValue: " + str);
            }
        }catch (NullPointerException error){
            Log.i("BleService","Error: " + error);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

}



