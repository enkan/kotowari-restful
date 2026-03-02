package kotowari.restful.example;

import enkan.system.EnkanSystem;
import enkan.system.command.JsonRequestCommand;
import enkan.system.command.SqlCommand;
import enkan.system.repl.PseudoRepl;
import enkan.system.repl.ReplBoot;
import enkan.system.repl.client.ReplClient;
import kotowari.system.KotowariCommandRegister;

/**
 * Entry point for the example application.
 *
 * <p>Two startup modes are available:
 * <ul>
 *   <li><b>Direct mode</b> (default, no arguments) — starts {@link EnkanSystem} directly and
 *       blocks until the JVM receives a shutdown signal (Ctrl-C). Useful for running the server
 *       as a plain process or from {@code mvn exec:java}.</li>
 *   <li><b>REPL mode</b> ({@code --repl} argument) — launches a {@code PseudoRepl} with the
 *       standard kotowari commands plus {@code sql} and {@code jsonRequest}, then opens an
 *       interactive REPL client. Useful for interactive development.</li>
 * </ul>
 */
public class DevMain {
    public static void main(String[] args) throws Exception {
        boolean replMode = args.length > 0 && "--repl".equals(args[0]);

        if (replMode) {
            startRepl();
        } else {
            startDirect();
        }
    }

    /**
     * Starts the EnkanSystem directly and registers a shutdown hook to stop it cleanly.
     * The main thread parks until the JVM shuts down (e.g. Ctrl-C).
     */
    private static void startDirect() throws InterruptedException {
        EnkanSystem system = new ExampleSystemFactory().create();
        system.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            system.stop();
        }, "shutdown-hook"));

        System.out.println("Server started. Press Ctrl-C to stop.");
        Thread.currentThread().join();
    }

    /**
     * Starts a PseudoRepl with kotowari commands and opens the interactive REPL client.
     */
    private static void startRepl() throws Exception {
        PseudoRepl repl = new PseudoRepl(ExampleSystemFactory.class.getName());

        ReplBoot.start(repl,
                new KotowariCommandRegister(),
                r -> {
                    r.registerCommand("sql", new SqlCommand());
                    r.registerCommand("jsonRequest", new JsonRequestCommand());
                });

        new ReplClient().start(repl.getPort());
    }
}
