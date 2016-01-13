package com.hazelcast.remotecontroller;

import org.apache.logging.log4j.LogManager;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;

public class Main {

    public static org.apache.logging.log4j.Logger LOG = LogManager.getLogger(Main.class);

    public static int PORT = 9701;

    public static RemoteControllerHandler handler;

    public static RemoteController.Processor processor;

    public static void main(String[] args) {
        try {
            handler = new RemoteControllerHandler();
            processor = new RemoteController.Processor(handler);

            Runnable simple = () -> {
                try {
                    TServerTransport serverTransport = new TServerSocket(PORT);
                    TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));

                    LOG.info("Starting Remote Controller Server on port:"+PORT);
                    server.serve();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };

            new Thread(simple).start();
        } catch (Exception x) {
            x.printStackTrace();
        }
    }
}
