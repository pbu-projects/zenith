package lol.pbu;

import io.micronaut.runtime.Micronaut;

public class Application {

    static {
        if (System.getProperty("logback.statusListenerClass") == null) {
            System.setProperty("logback.statusListenerClass", "ch.qos.logback.core.status.OnErrorConsoleStatusListener");
        }
    }

    public static void main(String[] args) {
        Micronaut.build(args)
                 .banner(false)
                 .start();
    }
}