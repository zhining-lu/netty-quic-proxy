package cn.wowspeeder;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.commons.cli.*;

public class Application {
    private static InternalLogger logger = InternalLoggerFactory.getInstance(Application.class);

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        Option option = new Option("s", "server", false, "server listen address");
        options.addOption(option);

        option = new Option("c", "client", false, "server connect address");
        options.addOption(option);

        option = new Option("conf", "config", true, "config file path default:conf/config.json");
        options.addOption(option);

        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(options, args);

        String configPath = commandLine.getOptionValue("conf", "conf/config.json");

        logger.info("config path:{}", configPath);
        try{
            if (commandLine.hasOption("s")) {
//            SWServer.getInstance().start(configPath);
                QuicServer.getInstance().start(configPath);
            } else if (commandLine.hasOption("c")) {
//            SWLocal.getInstance().start(configPath);
                QuicLocal.getInstance().start(configPath);
            } else {
                logger.error("not found run type");
            }
            logger.info("start success!");

        }catch (Exception e){
            logger.error(e);
            QuicLocal.getInstance().stop();
            System.exit(-1);
        }

    }
}
