package kotowari.restful.example;

import enkan.system.command.JsonRequestCommand;
import enkan.system.command.SqlCommand;
import enkan.system.repl.PseudoRepl;
import enkan.system.repl.ReplBoot;
import enkan.system.repl.client.ReplClient;
import kotowari.system.KotowariCommandRegister;

public class DevMain {
    public static void main(String[] args) throws Exception {
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
