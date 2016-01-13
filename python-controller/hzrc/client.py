import logging

from thrift import Thrift
from thrift.protocol import TBinaryProtocol
from thrift.transport import TSocket, TTransport

import RemoteController


class HzRemoteController(RemoteController.Iface):
    logger = logging.getLogger("HzRemoteController")

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
            self.logger.warn('%s' % tx.message)

    def terminateMember(self, clusterId, memberId):
        return self.remote_controller.terminateMember(clusterId, memberId)

    def terminateCluster(self, clusterId):
        return self.remote_controller.terminateCluster(clusterId)

    def startMember(self, clusterId):
        return self.remote_controller.startMember(clusterId)

    def splitMemberFromCluster(self, memberId):
        return self.remote_controller.splitMemberFromCluster(memberId)

    def shutdownMember(self, clusterId, memberId):
        return self.remote_controller.shutdownMember(clusterId, memberId)

    def shutdownCluster(self, clusterId):
        return self.remote_controller.shutdownCluster(clusterId)

    def mergeMemberToCluster(self, clusterId, memberId):
        return self.remote_controller.mergeMemberToCluster(clusterId, memberId)

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
