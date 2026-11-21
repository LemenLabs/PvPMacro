package com.pvpmacro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** NEW. Step-by-step trace in logs/latest.log (turn off with logSteps=false in config/pvpmacro.json). */
final class PvpLog {
    private static final Logger LOG = LoggerFactory.getLogger("PvpMacro");
    private PvpLog() {}

    static void info(String text) {
        if (Config.get().logSteps) LOG.info("{} {}", "[PvpMacro]", text);
    }
}
