from thrift import Thrift
from thrift.transport import TSocket
from thrift.transport import TTransport
from thrift.protocol import TBinaryProtocol

from hazelcast.remotecontroller import RemoteController
try:

    # Make socket
    transport = TSocket.TSocket('localhost', 9701)

    # Buffering is critical. Raw sockets are very slow
    transport = TTransport.TBufferedTransport(transport)

    # Wrap in a protocol
    protocol = TBinaryProtocol.TBinaryProtocol(transport)

    # Create a client to use the protocol encoder
    client = RemoteController.Client(protocol)

    # Connect!
    transport.open()

    print('ping result : {}'.format(client.ping()))

    # Close!
    transport.close()

except Thrift.TException as tx:
    print(('%s' % (tx.message)))