package org.learningstorm.log;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.learningstorm.log.bolt.IndexerBolt;
import org.learningstorm.log.bolt.VolumeCountingBolt;
import org.learningstorm.log.common.Conf;
import org.learningstorm.log.common.EmbeddedCassandra;
import org.learningstorm.log.common.UnitTestUtils;
import org.learningstorm.log.model.LogEntry;

import backtype.storm.utils.Utils;

import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Cluster;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;
import com.netflix.astyanax.ddl.KeyspaceDefinition;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;

import redis.clients.jedis.Jedis;

public class IntegerationTestTopology {
	public static final String REDIS_CHANNEL = "TestLogBolt";
	
	private static Jedis jedis;
	private static LogTopology topology = new LogTopology();
	private static TestBolt testBolt = new TestBolt(REDIS_CHANNEL);
	private static EmbeddedCassandra cassandra;
	private static Client client;
	
	@BeforeClass
	public static void setup() throws Exception {
		setupCassandra();
		setupElasticSearch();
		setupTopology();
	}

	private static void setupCassandra() throws Exception {
		cassandra = new EmbeddedCassandra(9171);
		cassandra.start();
		Thread.sleep(3000);
		
		AstyanaxContext<Cluster> clusterContext = new AstyanaxContext.Builder()
			.forCluster("ClusterName")
			.withAstyanaxConfiguration(
					new AstyanaxConfigurationImpl()
						.setDiscoveryType(NodeDiscoveryType.NONE))
			.withConnectionPoolConfiguration(
					new ConnectionPoolConfigurationImpl("MyConnectionPool")
						.setMaxConnsPerHost(1).setSeeds(
								"localhost:9171"))
			.withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
			.buildCluster(ThriftFamilyFactory.getInstance());
		
		clusterContext.start();
		
		Cluster cluster = clusterContext.getEntity();
		KeyspaceDefinition ksDef = cluster.makeKeyspaceDefinition();
		
		Map<String, String> startOptions = new HashMap<String, String>();
		startOptions.put("replication_factor", "1");
		ksDef.setName(Conf.LOGGING_KEYSPACE)
			.setStrategyClass("SimpleStrategy")
			.setStrategyOptions(startOptions)
			.addColumnFamily(
					cluster.makeColumnFamilyDefinition().setName(Conf.COUNT_CF_NAME)
						.setComparatorType("UTF8Type")
						.setKeyValidationClass("UTF8Type")
						.setDefaultValidationClass("CounterColumnType"));
		
		cluster.addKeyspace(ksDef);
		Thread.sleep(3000);
	}

	private static void setupElasticSearch() throws Exception {
		Node node = NodeBuilder.nodeBuilder().local(true).node();
		client = node.client();
		
		Thread.sleep(5000);
	}

	private static void setupTopology() {
		topology.getBuilder().setBolt("testBolt", testBolt, 1)
			.globalGrouping("indexer");
		
		topology.runLocal(0);
		jedis = new Jedis("localhost",
				Integer.parseInt(Conf.DEFAULT_JEDIS_PORT));
		
		jedis.connect();
		jedis.flushDB();
		
		Utils.sleep(5000);
	}
	
	@AfterClass
	public static void shutDown() {
		topology.shutDownLocal();
		jedis.disconnect();
		client.close();
		cassandra.stop();
	}
	
	@Test
	public void inputOutputClusterTest() throws Exception {
		String testData = UnitTestUtils.readFile("/testData1.json");
		
		jedis.rpush("log", testData);
		LogEntry entry = UnitTestUtils.getEntry();
		
		long minute = VolumeCountingBolt.getMinuteForTime(entry.getTimestamp());
		Utils.sleep(6000);
		
		String id = jedis.rpop(REDIS_CHANNEL);
		assertNotNull(id);
		
		GetResponse response = client
				.prepareGet(IndexerBolt.INDEX_NAME, IndexerBolt.INDEX_TYPE, id)
				.execute().actionGet();
		assertTrue(response.isExists());
		
		AstyanaxContext<Keyspace> astyContext = new AstyanaxContext.Builder()
		.forCluster("ClusterName")
		.forKeyspace(Conf.LOGGING_KEYSPACE)
		.withAstyanaxConfiguration(
				new AstyanaxConfigurationImpl()
					.setDiscoveryType(NodeDiscoveryType.NONE))
		.withConnectionPoolConfiguration(
				new ConnectionPoolConfigurationImpl("MyConnectionPool")
					.setMaxConnsPerHost(1).setSeeds(
							"localhost:9171"))
		.withConnectionPoolMonitor(new CountingConnectionPoolMonitor())
		.buildKeyspace(ThriftFamilyFactory.getInstance());
		
		astyContext.start();
		Keyspace ks = astyContext.getEntity();
		Column<String> result = ks
				.prepareQuery(
						new ColumnFamily<String, String>(
								Conf.COUNT_CF_NAME,
								StringSerializer.get(),
								StringSerializer.get()))
				.getKey(Long.toString(minute)).getColumn(entry.getSource())
				.execute().getResult();
		assertEquals(1L, result.getLongValue());
	}
	
	@Test
	public void testMustFailed() {
		fail();
	}
}
