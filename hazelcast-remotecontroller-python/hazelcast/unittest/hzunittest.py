import logging

from thrift import Thrift
from thrift.protocol import TBinaryProtocol
from thrift.transport import TSocket, TTransport

from hazelcast.remotecontroller import RemoteController


class HzRemoteController(RemoteController.Iface):
    logger = logging.getLogger("HzUnitTest")

    def __init__(self, host, port):
        try:
            # Make socket
            transport = TSocket.TSocket(host=host, port=port)
            # Buffering is critical. Raw sockets are very slow
            transport = TTransport.TBufferedTransport(transport)
            # Wrap in a protocol
            protocol = TBinaryProtocol.TBinaryProtocol(transport)
            self.remote_controller = RemoteController.Client(protocol)
            # Connect!
            transport.open()
        except Thrift.TException as tx:
            self.logger('%s' % tx.message)

    def terminateMember(self, clusterId, memberId, delay):
        return self.remote_controller.terminateMember(clusterId, memberId, delay)

    def terminateCluster(self, clusterId, delay):
        return self.remote_controller.terminateCluster(clusterId, delay)

    def startMember(self, clusterId, delay):
        return self.remote_controller.startMember(clusterId, delay)

    def splitMemberFromCluster(self, memberId, delay):
        return self.remote_controller.splitMemberFromCluster(memberId, delay)

    def shutdownMember(self, clusterId, memberId, delay):
        return self.remote_controller.shutdownMember(clusterId, memberId, delay)

    def shutdownCluster(self, clusterId, delay):
        return self.remote_controller.shutdownCluster(clusterId, delay)

    def mergeMemberToCluster(self, clusterId, memberId, delay):
        return self.remote_controller.mergeMemberToCluster(clusterId, memberId, delay)

    def executeOnController(self, clusterId, script, lang):
        return self.remote_controller.executeOnController(clusterId, script, lang)

    def createCluster(self, hzVersion, xmlconfig):
        return self.remote_controller.createCluster(hzVersion, xmlconfig)

    def ping(self):
        return self.remote_controller.ping()

    def clean(self):
        return self.remote_controller.clean()

    def exit(self):
        self.remote_controller.exit()
        self.remote_controller._iprot.trans.close()
