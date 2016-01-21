package com.hazelcast.remotecontroller;

import org.apache.logging.log4j.LogManager;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.ServerContext;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TServerEventHandler;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransport;

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

                    LOG.info("Starting Remote Controller Server on port:" + PORT);

                    //Set server event handler
                    server.setServerEventHandler(new ServerEventHandler(handler));
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

    private static class ServerEventHandler implements TServerEventHandler {

        private RemoteControllerHandler handler;

        public ServerEventHandler(RemoteControllerHandler handler) {

            this.handler = handler;
        }

        public void preServe() {
            LOG.info("TServerEventHandler.preServe - server starts accepting connections");
        }

        public ServerContext createContext(TProtocol input, TProtocol output) {
            return null;
        }

        public void deleteContext(ServerContext serverContext, TProtocol input, TProtocol output) {
            try {
                this.handler.clean();
            } catch (TException e) {
                LOG.error(e.getMessage());
            }
            LOG.info("TServerEventHandler.deleteContext client");
        }

        public void processContext(ServerContext serverContext, TTransport inputTransport, TTransport outputTransport) {
        }

    }

}
