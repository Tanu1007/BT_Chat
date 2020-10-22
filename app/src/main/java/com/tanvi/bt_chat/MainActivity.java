package com.tanvi.bt_chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button listen,send,listDevices;
    ListView listView;
    TextView msg_box,status;
    EditText writeMsg;
    Switch sw;

    BluetoothAdapter myBTAdapter;
    BluetoothDevice[] btArray;

    SendReceive sendReceive;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING=2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    int REQUEST_ENABLE_BT = 1;

    private static final String APP_NAME = "BT_Chat";
    private static final UUID MY_UUID = UUID.fromString("1cb829c4-3c9b-4970-9027-3aaa77df1daf");
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewIds();
        myBTAdapter = BluetoothAdapter.getDefaultAdapter();
        if(!myBTAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
        }

        implementListeners();

    }

    private void implementListeners() {
        listDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Set<BluetoothDevice> bt = myBTAdapter.getBondedDevices();
                String[] strings = new String[bt.size()];
                btArray=new BluetoothDevice[bt.size()];
                int index = 0;

                if(bt.size()>0){
                    for(BluetoothDevice device:bt){
                        btArray[index] = device;
                        strings[index] = device.getName();
                        index++;
                    }
                    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getApplication(),android.R.layout.simple_list_item_1,strings);
                    listView.setAdapter(arrayAdapter);
                }
            }
        });
        listen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ServerClass serverClass = new ServerClass();
                serverClass.start();
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                ClientClass clientClass= new ClientClass(btArray[i]);
                clientClass.start();

                status.setText("Connecting...");
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String string = String.valueOf(writeMsg.getText());
                sendReceive.write(string.getBytes());
                writeMsg.setText(" ");
            }
        });
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what){
                case STATE_LISTENING:
                    status.setText("Listening");
                    break;
                case STATE_CONNECTING:
                    status.setText("Connecting");
                    break;
                case STATE_CONNECTED:
                    status.setText("Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    status.setText("Connection Failed");
                    break;
                case STATE_MESSAGE_RECEIVED:
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff,0,msg.arg1);
                    msg_box.setText(tempMsg);
                    status.setText("Message Received");
                    break;
            }
            return true;
        }
    });

    private void findViewIds() {
        listen = (Button) findViewById(R.id.Button_listen);
        send = (Button) findViewById(R.id.button_send);
        listView = (ListView) findViewById(R.id.listView_Devices);
        msg_box = (TextView) findViewById(R.id.textView_message);
        status = (TextView) findViewById(R.id.textView_status);
        writeMsg = (EditText) findViewById(R.id.editText_msg);
        listView = (ListView) findViewById(R.id.listView_Devices);
        listDevices = (Button) findViewById(R.id.Button_list);

    }

    private class ServerClass extends Thread{
        private BluetoothServerSocket serverSocket;
        public  ServerClass(){
            try {
                serverSocket = myBTAdapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME,MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void run(){
            BluetoothSocket socket=null;
            while(socket==null){
                try {
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTING;
                    handler.sendMessage(message);
                    socket=serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }
                if(socket!=null){
                    Message message = Message.obtain();
                    message.what = STATE_CONNECTED;
                    handler.sendMessage(message);
                    sendReceive = new SendReceive(socket);
                    sendReceive.start();
                    break;
                }
            }
        }
    }

    private class ClientClass extends Thread{
        private BluetoothDevice device;
        private BluetoothSocket socket;

        public  ClientClass(BluetoothDevice device1){
            device=device1;
            try {
                socket=device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run(){
            try {
                socket.connect();
                Message message = Message.obtain();
                message.what = STATE_CONNECTED;
                handler.sendMessage(message);
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                e.printStackTrace();
                Message message = Message.obtain();
                message.what = STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }
    }

    private class SendReceive extends Thread{
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SendReceive(BluetoothSocket socket){
            bluetoothSocket = socket;
            InputStream tempIn = null;
            OutputStream tempOut = null;

            try {
                tempIn=bluetoothSocket.getInputStream();
                tempOut=bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            inputStream=tempIn;
            outputStream=tempOut;
        }
        public void run(){
            byte[] buffer = new byte[1024];
            int bytes;

            while (true){
                try {
                    bytes=inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED,bytes,-1,buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
        public void write(byte[] bytes){
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



    /*
    private void scanButton() {
        ButtonScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                myBTAdapter.startDiscovery();
            }
        });
        Toast.makeText(getApplicationContext(),"Scanning Bluetooth Devices",Toast.LENGTH_SHORT).show();
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(myReciever,intentFilter);

        arrayAdapter1=new ArrayAdapter<>(getApplicationContext(),android.R.layout.simple_list_item_1,stringArrayList);
        scanListView.setAdapter(arrayAdapter1);
    }

    BroadcastReceiver myReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device =intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Toast.makeText(getApplicationContext(),"Scanning Bluetooth Devices",Toast.LENGTH_SHORT).show();
                stringArrayList.add(device.getName());
                arrayAdapter1.notifyDataSetChanged();
            }
        }
    };
    private void executeButton() {
        ButtonShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Set<BluetoothDevice> bt = myBTAdapter.getBondedDevices();
                String[] string = new String[bt.size()];
                int ind=0;
                if(bt.size()>0){
                    for(BluetoothDevice device:bt){
                        string[ind]=device.getName();
                        ind++;
                    }
                    ArrayAdapter<String> arrayAdapter=new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1,string);
                    listView.setAdapter(arrayAdapter);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Request_enable_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(getApplicationContext(), "Bluetooth is Enable!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), "Bluetooth connection canceled!", Toast.LENGTH_SHORT).show();
            }
        }

    }

    private void BTOnMethod() {
        BTOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(myBTAdapter==null){
                    Toast.makeText(getApplicationContext(),"Bluetooth does on support on this device",Toast.LENGTH_SHORT).show();
                }
                else{
                    if(!myBTAdapter.isEnabled()){
                        startActivityForResult(enableBTIntent,Request_enable_BT);
                    }
                }
            }
        });
    }
    private void BTOffMethod() {
        BTOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(myBTAdapter.isEnabled()){
                    myBTAdapter.disable();
                    Toast.makeText(getApplicationContext(),"Bluetooth disabled!",Toast.LENGTH_SHORT).show();
                }
            }
        });

    }
    */

}