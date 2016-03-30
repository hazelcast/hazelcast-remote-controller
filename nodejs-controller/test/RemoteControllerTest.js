var HzRemoteController = require('../lib/Controller');
var expect = require('chai').expect;
describe('Remote Controller Test', function() {

    var controller;

    before(function() {
        controller = new HzRemoteController('localhost', 9701);
    });

    after(function(done) {
        controller.exit(function(err, result) {
            if (err == null) done();
        })
    });

    it('ping', function(done) {
        controller.ping(function(err, result) {
            expect(result).to.be.true;
            done(err);
        })
    });

    it('create cluster', function(done) {
        controller.createCluster(null, null, function(err, result) {
            expect(result).to.have.property('id');
            expect(result.id).to.not.be.null;
            done(err);
        });
    });

    it('start shutdown member', function(done) {
        this.timeout(10000);
        controller.createCluster(null, null, function(err, cluster) {
            controller.startMember(cluster.id, function(err, member) {
                expect(member).to.not.be.null;
                expect(member.uuid).to.not.be.null;
                expect(member.host).to.not.be.null;
                expect(member.port).to.not.be.null;
                controller.shutdownMember(cluster.id, member.uuid, function(err, result) {
                    expect(result).to.be.true;
                    done(err);
                })
            })
        })
    });

    it('terminate member', function(done) {
        this.timeout(10000);
        controller.createCluster(null, null, function(err, cluster) {
            controller.startMember(cluster.id, function(err, member) {
                controller.terminateMember(cluster.id, member.uuid, function(err, result) {
                    expect(result).to.be.true;
                    done(err);
                })
            })
        })
    });

    it('script executor', function(done) {
        var script = 'function echo() { return instance_0.getSerializationService().toBytes(1.0) }; result=echo();';
        controller.createCluster(null, null, function(err, cluster) {
            controller.startMember(cluster.id, function(err, member) {
                controller.executeOnController(cluster.id, script, 1, function(err, result) {
                    expect(result.success).to.be.true;
                    expect(result.result).to.be.an.instanceof(Object);
                    done(err);
                });
            });
        });
    });
});
