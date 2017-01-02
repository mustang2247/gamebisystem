package org.learningstorm.log;

import com.hmsonline.storm.cassandra.StormCassandraConstants;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.AlreadyAliveException;
import org.apache.storm.generated.AuthorizationException;
import org.apache.storm.generated.InvalidTopologyException;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.utils.Utils;
import org.learningstorm.log.bolt.CassandraCounterBatchingBolt;
import org.learningstorm.log.bolt.IndexerBolt;
import org.learningstorm.log.bolt.LogRulesBolt;
import org.learningstorm.log.bolt.VolumeCountingBolt;
import org.learningstorm.log.common.Conf;
import org.learningstorm.log.spout.LogSpout;

public class LogTopology {
    private TopologyBuilder builder = new TopologyBuilder();
    private Config conf = new Config();
    private LocalCluster cluster;

    public LogTopology() {
        builder.setSpout("logSpout", new LogSpout(), 10);
        builder.setBolt("logRules", new LogRulesBolt(), 10)
                .shuffleGrouping("logSpout");
        builder.setBolt("indexer", new IndexerBolt(), 10)
                .shuffleGrouping("logRules");
        builder.setBolt("counter", new VolumeCountingBolt(), 10)
                .shuffleGrouping("logRules");

        CassandraCounterBatchingBolt logPersistenceBolt =
                new CassandraCounterBatchingBolt(
                        Conf.LOGGING_KEYSPACE,
                        "",
                        Conf.COUNT_CF_NAME,
                        VolumeCountingBolt.FIELD_ROW_KEY,
                        VolumeCountingBolt.FIELD_INCREMENT);
        builder.setBolt("countPersistor", logPersistenceBolt, 10)
                .shuffleGrouping("counter");

        //初始化参数
        conf.put(Conf.REDIS_PORT_KEY, Conf.DEFAULT_JEDIS_PORT);
        conf.put(StormCassandraConstants.CASSANDRA_KEYSPACE, Conf.LOGGING_KEYSPACE);
    }

    public TopologyBuilder getBuilder() {
        return builder;
    }

    public LocalCluster getLocalCluster() {
        return cluster;
    }

    public Config getConf() {
        return conf;
    }

    public void runLocal(int runTime) {
        conf.setDebug(true);
        conf.put(Conf.REDIS_HOST_KEY, "localhost");
        conf.put(StormCassandraConstants.CASSANDRA_HOST, "localhost:9171");
        cluster = new LocalCluster();
        cluster.submitTopology("test", conf, builder.createTopology());
        if (runTime > 0) {
            Utils.sleep(runTime);
            shutDownLocal();
        }
    }

    public void shutDownLocal() {
        if (cluster != null) {
            cluster.killTopology("test");
            cluster.shutdown();
        }
    }

    public void runCluster(String name, String redisHost, String cassandraHost)
            throws InvalidTopologyException, AuthorizationException, AlreadyAliveException {
        conf.setNumWorkers(20);
        conf.put(Conf.REDIS_HOST_KEY, redisHost);
        conf.put(StormCassandraConstants.CASSANDRA_HOST, cassandraHost);
        StormSubmitter.submitTopology(name, conf, builder.createTopology());
    }

    public static void main(String[] args) throws Exception {

        LogTopology topology = new LogTopology();

        if (args != null && args.length > 1) {
            topology.runCluster(args[0], args[1], args[2]);
        } else {
            if (args != null && args.length == 1)
                System.out
                        .println("Running in local mode, redis ip missing for cluster run");
            topology.runLocal(10000);
        }

    }

}