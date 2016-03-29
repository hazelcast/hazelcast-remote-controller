import unittest

from hzrc.client import HzRemoteController
from hzrc.ttypes import *


class RemoteControllerTestCase(unittest.TestCase):
    controller = None

    @classmethod
    def setUpClass(cls):
        cls.controller = HzRemoteController('localhost', 9701)

    # @classmethod
    # def tearDownClass(cls):
        # cls.controller.exit()

    def test_ping(self):
        result = self.controller.ping()
        self.assertTrue(result)

    def test_createCluster(self):
        result = self.controller.createCluster(None, None)
        self.assertTrue(result)

    def test_startMember(self):
        cluster = self.controller.createCluster(None, None)
        member = self.controller.startMember(cluster.id)
        self.assertTrue(member)
        self.assertTrue(member.uuid)
        self.assertTrue(member.host)
        self.assertTrue(member.port)

    def test_shutdownMember(self):
        cluster = self.controller.createCluster(None, None)
        member = self.controller.startMember(cluster.id)
        res = self.controller.shutdownMember(cluster.id, member.uuid)
        self.assertTrue(res)

    def test_terminateMember(self):
        cluster = self.controller.createCluster(None, None)
        member = self.controller.startMember(cluster.id)
        res = self.controller.terminateMember(cluster.id, member.uuid)
        self.assertTrue(res)

    def test_shutdownMember_multiple(self):
        cluster = self.controller.createCluster(None, None)
        member1 = self.controller.startMember(cluster.id)
        member2 = self.controller.startMember(cluster.id)
        member3 = self.controller.startMember(cluster.id)
        res1 = self.controller.shutdownMember(cluster.id, member1.uuid)
        res2 = self.controller.shutdownMember(cluster.id, member2.uuid)
        res3 = self.controller.shutdownMember(cluster.id, member3.uuid)
        self.assertTrue(res1)
        self.assertTrue(res2)
        self.assertTrue(res3)

    def test_script_executor(self):
        cluster = self.controller.createCluster(None, None)
        member = self.controller.startMember(cluster.id)
        script = """
def echo():
    return instance_0.getSerializationService().toBytes(1.0)
result=echo()
"""
        response = self.controller.executeOnController(cluster.id, script, Lang.PYTHON)


        print(response)
        # def test_multi_cluster(self):
        #     cluster_1 = self.controller.createCluster(None, None)
        #     member_1_1 = self.controller.startMember(cluster_1.id)
        #     member_1_2 = self.controller.startMember(cluster_1.id)
        #
        #     cluster_2 = self.controller.createCluster(None, None)
        #     member_2_1 = self.controller.startMember(cluster_2.id)
        #     member_2_2 = self.controller.startMember(cluster_2.id)
        #
        #     self.assertTrue(member_2_1)
        #     self.assertTrue(member_2_2)
        #     self.assertTrue(member_1_1)
        #     self.assertTrue(member_1_2)


if __name__ == '__main__':
    unittest.main()
