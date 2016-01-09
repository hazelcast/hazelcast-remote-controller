import unittest

from hzrc.client import HzRemoteController


class RemoteControllerTestCase(unittest.TestCase):
    controller = None

    @classmethod
    def setUpClass(cls):
        cls.controller = HzRemoteController("localhost", 9701)

    @classmethod
    def tearDownClass(cls):
        cls.controller.exit()

    def test_ping(self):
        result = self.controller.ping()
        self.assertTrue(result)

    def test_createCluster(self):
        result = self.controller.createCluster(None, None)
        self.assertTrue(result)

    def test_startMember(self):
        cluster = self.controller.createCluster(None, None)
        member = self.controller.startMember(cluster.id, None)
        self.assertTrue(member)
        self.assertTrue(member.uuid)
        self.assertTrue(member.host)
        self.assertTrue(member.port)

    def test_shutdownMember(self):
        cluster = self.controller.createCluster(None, None)
        member = self.controller.startMember(cluster.id, None)
        res = self.controller.shutdownMember(cluster.id, member.uuid, None)
        self.assertTrue(res)

    def test_terminateMember(self):
        cluster = self.controller.createCluster(None, None)
        member = self.controller.startMember(cluster.id, None)
        res = self.controller.terminateMember(cluster.id, member.uuid, None)
        self.assertTrue(res)

    def test_shutdownMember_multiple(self):
        cluster = self.controller.createCluster(None, None)
        member1 = self.controller.startMember(cluster.id, None)
        member2 = self.controller.startMember(cluster.id, None)
        member3 = self.controller.startMember(cluster.id, None)
        res1 = self.controller.shutdownMember(cluster.id, member1.uuid, None)
        res2 = self.controller.shutdownMember(cluster.id, member2.uuid, None)
        res3 = self.controller.shutdownMember(cluster.id, member3.uuid, None)
        self.assertTrue(res1)
        self.assertTrue(res2)
        self.assertTrue(res3)

if __name__ == '__main__':
    unittest.main()
