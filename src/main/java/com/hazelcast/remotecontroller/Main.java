package com.hazelcast.remotecontroller;

import org.apache.logging.log4j.LogManager;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.ServerContext;
import org.apache.thrift.server.TNonblockingServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TServerEventHandler;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

public class Main {

    private static final String USE_SIMPLE_SERVER_ARG = "--use-simple-server";

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
                    TServer server;
                    if (shouldUseSimpleServer(args)) {
                        server = createSimpleServer(processor);
                    } else {
                        server = createNonblockingServer(processor);
                    }

                    server.setServerEventHandler(new ServerEventHandler(handler));

                    LOG.info("Starting Remote Controller Server on port:" + PORT);

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

    private static boolean shouldUseSimpleServer(String[] args) {
        return args != null && args.length > 0 && USE_SIMPLE_SERVER_ARG.equals(args[0]);
    }

    private static TServer createNonblockingServer(RemoteController.Processor processor) throws TTransportException {
        TNonblockingServerSocket socket = new TNonblockingServerSocket(PORT);

        TNonblockingServer.Args tnbArgs = new TNonblockingServer.Args(socket);
        tnbArgs.processor(processor);

        tnbArgs.transportFactory(new TFramedTransport.Factory(Integer.MAX_VALUE));
        tnbArgs.protocolFactory(new TBinaryProtocol.Factory());

        return new TNonblockingServer(tnbArgs);
    }

    private static TServer createSimpleServer(RemoteController.Processor processor) throws TTransportException {
        TServerSocket socket = new TServerSocket(PORT);

        TSimpleServer.Args tssArgs = new TSimpleServer.Args(socket);
        tssArgs.processor(processor);

        return new TSimpleServer(tssArgs);
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
            LOG.info("TServerEventHandler.createContext - new client context created");
            return null;
        }

        public void deleteContext(ServerContext serverContext, TProtocol input, TProtocol output) {
            try {
                this.handler.clean();
            } catch (TException e) {
                LOG.error(e);
            }
            LOG.info("TServerEventHandler.deleteContext client");
        }

        public void processContext(ServerContext serverContext, TTransport inputTransport, TTransport outputTransport) {
        }

    }

}
