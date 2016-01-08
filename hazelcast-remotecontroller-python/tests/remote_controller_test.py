import unittest

from hazelcast.unittest.hzunittest import HzRemoteController


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
        result = self.controller.createCluster("LATEST", "xmlconfig")
        self.assertTrue(result)

if __name__ == '__main__':
    unittest.main()
