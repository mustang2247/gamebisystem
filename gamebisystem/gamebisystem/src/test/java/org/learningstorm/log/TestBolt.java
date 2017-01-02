package org.learningstorm.log;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;
import org.learningstorm.log.common.Conf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.Map;

public class TestBolt extends BaseRichBolt {
	private static final long serialVersionUID = 8382663822434509103L;
	@SuppressWarnings("unused")
	private static final transient Logger LOG = LoggerFactory.getLogger(TestBolt.class);
	private static Jedis jedis;
	private String channel;
	
	public TestBolt(String channel) {
		this.channel = channel;
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public void prepare(Map stormConf, TopologyContext context,
			OutputCollector collector) {
		jedis = new Jedis("localhost", Integer.parseInt(Conf.DEFAULT_JEDIS_PORT));
		jedis.connect();
	}

	@Override
	public void execute(Tuple input) {
		jedis.rpush(channel, input.getString(1));
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
	}

}
