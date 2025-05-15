package com.toyota.tcpserver;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

public class ClientHandlerTest {

    private Socket mockSocket;
    private RatePublisher mockRatePublisher;
    private ClientHandler clientHandler;
    private ByteArrayOutputStream socketOutputStream;
    private PrintWriter outToClient;

}
