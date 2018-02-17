package sample;

import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends Application {

    //Work as a client searching for servers (connected devices that have this app)
    void getAvailableDevices(){
        Thread broadCastReceiver  = new Thread(new Runnable() {
            @Override
            public void run() {

                DatagramSocket c;
                // Find the server using UDP broadcast
                try {
                    //Open a random port to send the package
                    c = new DatagramSocket();
                    c.setBroadcast(true);

                    byte[] sendData = "DISCOVER_EXPRESS_REQUEST".getBytes();

                    //Try the 255.255.255.255 first
//                    try {
//                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), 8888);
//                        c.send(sendPacket);
//                        System.out.println(Client.class.getClass().getName() + ">>> Request packet sent to: 255.255.255.255 (DEFAULT)");
//                    } catch (Exception e) {
//                    }

                    // Broadcast the message over all the network interfaces
                    Enumeration interfaces = NetworkInterface.getNetworkInterfaces();
                    while (interfaces.hasMoreElements()) {
                        NetworkInterface networkInterface = (NetworkInterface) interfaces.nextElement();

                        if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                            continue; // Don't want to broadcast to the loopback interface
                        }

                        for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                            InetAddress broadcast = interfaceAddress.getBroadcast();
                            if (broadcast == null) {
                                continue;
                            }

                            // Send the broadcast package!
                            try {
                                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, broadcast, 8888);
                                c.send(sendPacket);
                            } catch (Exception e) {
                            }

                            System.out.println(">>> Request packet sent to: " + broadcast.getHostAddress() + "; Interface: " + networkInterface.getDisplayName());
                        }
                    }

                    System.out.println(">>> Done looping over all network interfaces. Now waiting for a reply!");

                    while(true) {

                        //Wait for a response
                        byte[] recvBuf = new byte[15000];
                        DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
                        c.receive(receivePacket);

                        //We have a response
                        System.out.println(">>> Broadcast response from server: " + receivePacket.getAddress().getHostAddress());

                        //Check if the message is correct
                        String message = new String(receivePacket.getData()).trim();

                        if (message.equals("DISCOVER_EXPRESS_RESPONSE")) {

                            //DO SOMETHING WITH THE SERVER'S IP (for example, store it in your controller)
                            System.out.println("Found Express on: "+receivePacket.getAddress());
                            int i=0;
                            for(i=0;i<5;i++) {
                                byte[] recvPort = new byte[4];
                                DatagramPacket receivePort = new DatagramPacket(recvPort, recvPort.length);
                                c.receive(receivePort);
                                int port = ByteBuffer.wrap(receivePort.getData()).getInt();
                                System.out.println("connect on port: " + port);
                                try (ServerSocket ss = new ServerSocket(port)) {
                                    byte[] acceptConnectionData = "ACCEPT".getBytes();
                                    DatagramPacket confirmPortPacket = new DatagramPacket(acceptConnectionData, acceptConnectionData.length, receivePacket.getAddress(), receivePacket.getPort());
                                    c.send(confirmPortPacket);
                                    break;
                                } catch (IOException e) {
                                    byte[] rejectConnectionData = "REJECT".getBytes();
                                    DatagramPacket confirmPortPacket = new DatagramPacket(rejectConnectionData, rejectConnectionData.length, receivePacket.getAddress(), receivePacket.getPort());
                                    c.send(confirmPortPacket);
                                }
                            }
                            if(i!=5){
                                System.out.println("Connection Established");
                            }else{
                                System.out.println("Connection Cannot be Established");
                            }

                        }

                    }
//
//                    //Close the port!
//
//                    c.close();

                } catch (IOException ex) {

                }
            }
        });

        broadCastReceiver.start();
    }

    //Work as a server waiting for clients to send a discovery UPD broadcast packet
    void readyForPairing(){
        Thread broadcastReceiver = new Thread(new Runnable() {
            DatagramSocket socket;
            @Override
            public void run() {
                try{
                    //Keep a socket open to listen to all the UDP trafic that is destined for this port as broadcast
                    socket = new DatagramSocket(8888, InetAddress.getByName("0.0.0.0"));
                    socket.setBroadcast(true);

                    while (true) {

//                        System.out.println(getClass().getName() + ">>>Ready to receive broadcast packets!");

                        //Receive a packet
                        byte[] recvBuf = new byte[15000];

                        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);

                        try {
                            System.out.println("Waiting for requests on server...");
                            socket.receive(packet);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        //See if the packet holds the right command (message)

                        String message = new String(packet.getData()).trim();

                        if (message.equals("DISCOVER_EXPRESS_REQUEST")) {
                            System.out.println(packet.getAddress().getHostName()+" is searching for server");

                            byte[] sendDataResponse = "DISCOVER_EXPRESS_RESPONSE".getBytes();
                            DatagramPacket sendPacket = new DatagramPacket(sendDataResponse, sendDataResponse.length, packet.getAddress(), packet.getPort());
                            socket.send(sendPacket);
                            for(int i=0;i<5;i++) {
                                ServerSocket s = new ServerSocket(0);
                                byte[] port = ByteBuffer.allocate(4).putInt(s.getLocalPort()).array();
                                DatagramPacket sendPortPacket = new DatagramPacket(port, port.length, packet.getAddress(), packet.getPort());
                                socket.send(sendPortPacket);
                                byte[] confirm = new byte[10];
                                DatagramPacket receiveConfirmConnectionPacket = new DatagramPacket(confirm, confirm.length);
                                socket.receive(receiveConfirmConnectionPacket);
                                String confirmMessage = new String(receiveConfirmConnectionPacket.getData()).trim();
                                System.out.println(confirmMessage);
                                if(confirmMessage.equals("ACCEPT")){
                                    System.out.println("Connection Established on port: "+s.getLocalPort());
                                    break;
                                }else{
                                    System.out.println("Try again");
                                }
                            }
                        }

                    }

                } catch(IOException ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        broadcastReceiver.start();
    }

    @Override
    public void start(Stage primaryStage) throws Exception{

        readyForPairing();
        getAvailableDevices();


//        Parent root = FXMLLoader.load(getClass().getResource("sample.fxml"));
//        primaryStage.setTitle("Hello World");
//        primaryStage.setScene(new Scene(root, 300, 275));
//        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}
