package haven;

import groovy.lang.Binding;
import groovy.transform.ThreadInterrupt;
import groovy.util.GroovyScriptEngine;
import groovy.util.ResourceException;
import groovy.util.ScriptException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

public class Maid {
    private static Maid instance = null;

    private final Object LOCK = new Object();
    private final String scripts_folder;
    private final String scripts[];
    private final GroovyScriptEngine engine;
    private final Binding binding;
    private final ThreadGroup taskGroup;
    private HavenPanel haven;
    private Thread task, wait;
    //private TaskListener taskListener;
    //private CursorListener cursorListener;
    //private MeterListener meterListener;
    //private ItemListener itemListener;
    //private WidgetListener<?> widgetListener;
    private int menuGridId = 0;

    public Maid() {
        taskGroup = new ThreadGroup("groovy");

        binding = new Binding();
        binding.setVariable("maid", this);

        Properties p = initConfig();

        scripts = initScripts(p);
        scripts_folder = initScriptFolder(p);

        engine = initGroovy(scripts_folder);
        engine.getConfig().addCompilationCustomizers(
                new org.codehaus.groovy.control.customizers.ASTTransformationCustomizer(ThreadInterrupt.class));
    }

    public static Maid getInstance() {
        if (instance == null) {
            instance = new Maid();
        }

        return instance;
    }

    private Properties initConfig() {
        Properties p = new Properties();

        File inputFile = new File("maid.conf");
        if (!inputFile.exists()) {
            return p;
        }

        try {
            p.load(new FileInputStream(inputFile));
        } catch (IOException e) {
        }

        return p;
    }

    private String[] initScripts(Properties p) {
        String[] s = new String[12];
        for (int i = 1; i <= 12; i++) {
            s[i - 1] = p.getProperty("script_f" + i, "f" + i);
        }
        return s;
    }

    private String initScriptFolder(Properties p) {
        return p.getProperty("scripts_folder", "scripts");
    }

    private GroovyScriptEngine initGroovy(String scripts_folder) {
        GroovyScriptEngine gse;
        try {
            gse = new GroovyScriptEngine(scripts_folder);
        } catch (IOException e) {
            //doErr("Can't open scripts folder. I will try creating it...");

            boolean success = new File(scripts_folder).mkdir();

            if (success) {
                try {
                    gse = new GroovyScriptEngine(scripts_folder);
                } catch (IOException e2) {
                   // doErr("Directory \"" + scripts_folder + "\" gives errors. I give up.");

                    throw new RuntimeException("Can't initialize groovy script engine.", e2);
                }
            } else {
                //doErr("Can't read/create \"" + scripts_folder + "\".");

                throw new RuntimeException("Can't initialize groovy script engine.", e);
            }
        }

        return gse;
    }

    public void doSay(Object text) {
        System.out.println(text);
    }

    public void doErr(Object text) {
        System.err.println(text);
    }

    void doTask(final String name) {
        if (task != null) {
            doSay("-- Already running a task.");
            return;
        }
        task = new Thread(taskGroup, "maid") {

            @Override
            public void run() {
                try {
                    preProcessing();

                    engine.run(name + ".groovy", binding);

                    postProcessing();

                    doSay("-- Done\n");
                } catch (ResourceException e) {
                    doSay("Can't find the file.");

                    e.printStackTrace();
                } catch (ScriptException e) {
                    doSay("Something is wrong with this task. I don't understand it.");

                    e.printStackTrace();
                } catch (Throwable t) {
                    doErr("Canceled?");

                    t.printStackTrace();
                } finally {
                    task = null;
                }
            }
        };

        task.start();
    }

    void doTask(int i) {
        doTask(scripts[i]);
    }

    void stopTask() {
        if (task != null) {
            if (wait == null) {
                doSay("Interruping...");
                wait = new Thread() {

                    @Override
                    public void run() {
                        doSay(task.toString());

                        task.getThreadGroup().interrupt();

                        wait = null;

                        postProcessing();

                        doSay("Interrupted successfuly.");
                    }
                };

                wait.start();
            } else {
                doSay("Already interrumpting.");
            }

        } else {
            doSay("Nothing to interrupt.");
        }
    }

    private void preProcessing() {
    }

    private void postProcessing() {
        //clearListeners();
    }

    static {
        Console.setscmd("scstart", (cons, args) -> {
            if (args.length < 1) {
                System.out.println("No scriptname given.");
            }
            Maid.getInstance().doTask(args[1]);
        });

        Console.setscmd("scstop", (cons, args) -> {
            Maid.getInstance().stopTask();
        });
    }
}
