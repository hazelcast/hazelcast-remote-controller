package com.hazelcast.remotecontroller.test;

import com.hazelcast.remotecontroller.RemoteController;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

public class ClientRemoteControllerTest {

    public static void main(String[] args) {
        try {
            System.out.println("Connection remote server");
            TTransport transport = new TSocket("localhost", 9701);

            transport.open();

            TProtocol protocol = new TBinaryProtocol(transport);
            RemoteController.Client client = new RemoteController.Client(protocol);

            client.createCluster(null, null);

            System.out.println("remote ping result:" + client.ping());

            transport.close();
        } catch (TException x) {
            x.printStackTrace();
        }
    }

}
