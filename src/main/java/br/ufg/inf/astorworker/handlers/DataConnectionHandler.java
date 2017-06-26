package br.ufg.inf.astorworker.handlers;

import java.util.concurrent.BlockingQueue;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

public class DataConnectionHandler extends Thread {
	private static ServerSocket server;
	private static BlockingQueue<Socket> sockets;
	private Logger logger = Logger.getLogger(DataConnectionHandler.class);

	public DataConnectionHandler(int port) throws Exception {
		server = new ServerSocket(port);
		sockets = new LinkedBlockingQueue<Socket>(); 
	}

	@Override
	public void run(){
		try {
			while(true){
				Socket socket = server.accept();
				DataConnectionHandler.putSocket(socket);
				logger.info("Connection received: " + socket.getRemoteSocketAddress());
			}
		}

		catch(Exception e){
			logger.info("DataConnectionHandler had problems");
			e.printStackTrace();
		}
	}

	public static Socket getSocket() throws InterruptedException {
		synchronized(sockets){
			while(sockets.isEmpty())
				sockets.wait();
			return sockets.poll();
		}
	}	

	public static void putSocket(Socket s) throws InterruptedException {
		synchronized(sockets){
			sockets.put(s);
			if(sockets.size() == 1)
				sockets.notify();
		}
	}
}