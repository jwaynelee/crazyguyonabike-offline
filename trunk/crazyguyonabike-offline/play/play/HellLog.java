package play;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

public class HellLog {
	public static void main(String[] args) {
		PropertyConfigurator.configure(HellLog.class.getResource("log.conf"));
		Log log = LogFactory.getLog(HellLog.class);
		log.debug("Hello1");
		for (int i = 0; i < 100; ++i) {
			log.debug("hello hello hello hello hello");
			log.info("hello hello hello hello hello");
		}
	}
}
