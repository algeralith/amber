package haven;

import groovy.lang.Binding;
import groovy.transform.ThreadInterrupt;
import groovy.util.GroovyScriptEngine;
import groovy.util.ResourceException;
import groovy.util.ScriptException;
import haven.event.*;
import org.codehaus.groovy.control.customizers.*;
import java.util.concurrent.*;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaidEngine {

    private static MaidEngine instance = null;

    private Maid maid = null;

    private GroovyScriptEngine groovyScriptEngine;
    private Binding binding;

    private final String scriptsFolder = "scripts";

    private ThreadGroup taskGroup;
    private Thread task, wait;

    //public static UI ui = null;

    private static final Pattern progress = Pattern.compile("gfx/hud/prog/(\\d+)");
    private static final Pattern cursorName = Pattern.compile("gfx/hud/curs/(.+)");

    protected MaidEngine() {
        initEngine();
    }

    public static MaidEngine getInstance() {
        if (instance == null) {
            instance = new MaidEngine();
        }

        return instance;
    }

    private void initEngine() {
        System.out.println("Initializing Scripting Engine.");

        try {
            taskGroup = new ThreadGroup("groovy");
            groovyScriptEngine = new GroovyScriptEngine(scriptsFolder);

            binding = new Binding();
            binding.setVariable("maid", Maid.getInstance());

            groovyScriptEngine.getConfig().addCompilationCustomizers(new ASTTransformationCustomizer(ThreadInterrupt.class));

            //Set up maid
            maid = Maid.getInstance();

        } catch (IOException e) {
            //Can not open script folder
            e.printStackTrace();
        }
    }

    void doTask(final String name) {
        if (task != null) {
            Maid.getInstance().doSay("-- Already running a task.");
            return;
        }
        task = new Thread(taskGroup, "maid") {

            @Override
            public void run() {
                try {
                    maid.preProcessing();

                    groovyScriptEngine.run(name + ".groovy", binding);

                    maid.postProcessing();

                    maid.doSay("-- Done\n");
                } catch (ResourceException e) {
                    maid.doSay("Can't find the file.");

                    e.printStackTrace();
                } catch (ScriptException e) {
                    maid.doSay("Something is wrong with this task. I don't understand it.");

                    e.printStackTrace();
                } catch (Throwable t) {
                    maid.doErr("Canceled?");

                    t.printStackTrace();
                } finally {
                    task = null;
                }
            }
        };

        task.start();
    }

    void stopTask() {
        if (task != null) {
            if (wait == null) {
                maid.doSay("Interruping...");
                wait = new Thread() {

                    @Override
                    public void run() {
                        maid.doSay(task.toString());

                        task.getThreadGroup().interrupt();

                        wait = null;

                        maid.postProcessing();

                        maid.doSay("Interrupted successfuly.");
                    }
                };

                wait.start();
            } else {
                maid.doSay("Already interrumpting.");
            }

        } else {
            maid.doSay("Nothing to interrupt.");
        }
    }

    //Maid interceptions
    public void uimsg(int id, String msg, Object... args) {
        System.out.print("uimsg:\tid: " + id + " \tmsg: " + msg + " \targs:");
        for (int i = 0; i < args.length; i++) {
            System.out.print(" " + args[i]);
        }
        System.out.println();

        if ("prog".equals(msg) && maid.getTaskListener() != null) {
            if (args.length > 0) {
                onImgChange(maid.getTaskListener(), -666, ((Integer) args[0]).toString());
                //if (((Integer) args[0]) == 100) onImgDestroy(maid.getTaskListener(), -666);
            } else {
                onImgDestroy(maid.getTaskListener(), -666);
            }
        } else if ("tip".equals(msg)) {
            String arg = (String)args[0];
            String [] argsplit = arg.split("(:\\s)|(%)");
            if (argsplit[0].equals("Stamina")) {
                maid.stamina = Integer.parseInt(argsplit[1]);
            } else if (argsplit[0].equals("Energy")) {
                maid.energy = Integer.parseInt(argsplit[1]);
            }
        }

        if (Config.getEnableDebugMode() && !msg.equals("glut")) {
            System.out.print("uimsg\tid: " + id + "\tmsg:" + msg);
        }
        for (int i = 0; i < args.length; i++) {
            if (Config.getEnableDebugMode() && !msg.equals("glut")) System.out.print(" " + args[i]);
        }
        if (Config.getEnableDebugMode() && !msg.equals("glut")) System.out.println();

        if ("err".equals(msg)) {
            maid.errored = true;
            maid.error = (String)args[0];


            //Reset listeners
            maid.clearListeners();

            //Unsleep thread
            maid.wakeup();
        }

        try {
            Widget wdg = maid.getUI().widgets.get(id);
            if (maid.getMeterListener() != null && wdg instanceof IMeter && "set".equals(msg)) {
                onIMeterChange(maid.getMeterListener(), (IMeter) wdg, args);
            } else if (maid.getTaskListener() != null && wdg instanceof Img && "ch".equals(msg)) {
                onImgChange(maid.getTaskListener(), id, (String) args[0]);
            } else if (maid.getCursorListener() != null && "curs".equals(msg)) {
                onCursChange(maid.getCursorListener(), (String) args[0]);
            } else if (maid.getCombatListener() != null && "used".equals(msg)){
                onAttackDone(maid.getCombatListener(), wdg);
            } else if (maid.getItemListener() != null && "chres".equals(msg)) {
                onItemChange(maid.getItemListener(), (GItem) maid.getUI().widgets.get(id), "chres");
            }
        } catch (Throwable t) {
            errorInEventProcessing(t);
        }
    }

    public void newwidget(int id, String type, int parent, Object[] pargs, Object... cargs) throws InterruptedException {
        System.out.print("newwidget:\tid: " + id + " \ttype: " + type + " \tparent: " + parent + " \tpargs:");
        for (int i = 0; i < pargs.length; i++) {
            System.out.print(" " + pargs[i]);
        }
        System.out.print(" cargs:");
        for (int i = 0; i < cargs.length; i++) {
            System.out.print(" " + cargs[i]);
        }
        System.out.println();

        if ("gameui".equals(type)) {
            maid.setMenuGridId(id);
        } else {
            try {
                if (maid.getTaskListener() != null && "img".equals(type)) {
                    onImgChange(maid.getTaskListener(), id, (String) pargs[0]);
                } else if (maid.getWidgetListener() != null && "sm".equals(type)) {
                    onWidgetCreate(maid.getWidgetListener(), maid.getUI().widgets.get(id));
                    onWidgetCreate(maid.getWidgetListener(), (FlowerMenu)maid.getUI().widgets.get(id));
                } else if (maid.getItemListener() != null && "item".equals(type)) {
                    onItemChange(maid.getItemListener(), (GItem) maid.getUI().widgets.get(id), "created");
                } else if (maid.getWindowListener() != null && "wnd".equals(type)) {
                    onWindowCreate(maid.getWindowListener(), (Window)maid.getUI().widgets.get(id));
                } else if (maid.getCombatListener() != null && "fsess".equals(type)){
                    onCombatCreate(maid.getCombatListener(), (Fightsess)maid.getUI().widgets.get(id));
                }
            } catch (Throwable t) {
                errorInEventProcessing(t);
            }
        }
    }

    public void rcvmsg(int id, String msg, Object... args) {
        System.out.print("rcvmsg:\tid: " + id + " \tmsg: " + msg + " \targs:");
        for (int i = 0; i < args.length; i++) {
            System.out.print(" " + args[i]);
        }
        System.out.println();

        try {
            if (maid.getClickListener() != null && "click".equals(msg)) {
                if (args.length == 4) {
                    onMapClick(maid.getClickListener(), (Coord)args[1], (int)args[2], (int)args[3]);
                } else if (args.length == 9) {
                    onMapClick(maid.getClickListener(), maid.getGob((long)(int)args[5]), (int)args[2], (int)args[3]);
                }
            }
        } catch (Throwable t) {
            errorInEventProcessing(t);
        }
    }

    public void destroy(int id) {
        /*if (Config.getEnableDebugMode())*/ System.out.println("destroy\tid: " + id);
        Widget wdg = maid.getUI().widgets.get(id);

        try {
            if (maid.getTaskListener() != null && wdg instanceof Img) {
                onImgDestroy(maid.getTaskListener(), id);
            } else if (maid.getItemListener() != null && wdg instanceof GItem) {
                onItemChange(maid.getItemListener(), (GItem) wdg, "destroy");
            } else if (maid.getWindowListener() != null && wdg instanceof Window) {
                onWindowDestroy(maid.getWindowListener(), (Window)wdg);
            } else if (maid.getCombatListener() != null && wdg instanceof Fightsess){
                onCombatDestroy(maid.getCombatListener(), (Fightsess)wdg);
            }
        } catch (Throwable t) {
            errorInEventProcessing(t);
        }
    }

    public void onItemChange(ItemListener il, GItem gi, String msg){

        if(msg.equals("destroy")){
            onItemDestroy(maid.getItemListener(), gi);
        } else if(msg.equals("created")){
            onItemDisplay(maid.getItemListener(), gi);
        } else if(msg.equals("chres")){
            il.onItemChange(new ItemEvent(ItemEvent.Type.GRAB, gi));
        }
    }

    private void errorInEventProcessing(Throwable t) {
        t.printStackTrace();

        maid.doErr("Error processing events, canceling everything");
        maid.clearListeners();
        //maid.stopTask();
    }

    private void onItemDisplay(ItemListener l, GItem item) {
        // parent.parent might not be correct, further testing needed
        if(l == null){
            System.out.println("ItemListener is null");
        }
        if(item == null){
            System.out.println("GItem is null");
        }
        if (item.parent.parent instanceof RootWidget) {
            l.onItemGrab(new ItemEvent(ItemEvent.Type.GRAB, item));
        } else {
            l.onItemCreate(new ItemEvent(ItemEvent.Type.CREATE, item));
        }
    }

    private void onItemDestroy(ItemListener l, GItem item) {
        // parent.parent might not be correct, further testing needed
        if (item.parent.parent instanceof RootWidget)
            l.onItemRelease(new ItemEvent(ItemEvent.Type.RELEASE, item));
        else
            l.onItemDestroy(new ItemEvent(ItemEvent.Type.DESTROY, item));
    }

    private void onWindowCreate(WindowListener l, Window window) {
        l.onWindowCreate(new WindowEvent(WindowEvent.Type.CREATE, window));
    }

    private void onWindowDestroy(WindowListener l, Window window) {
        l.onWindowCreate(new WindowEvent(WindowEvent.Type.DESTROY, window));
    }

    private void onIMeterChange(MeterListener l, IMeter im, Object[] args) {
        String name = "tempname";
        //String name = im.bg.name;
        int values[] = new int[args.length / 2];
        for (int i = 0; i < values.length; i++) {
            values[i] = (Integer) args[i * 2 + 1];
        }

        if ("gfx/hud/meter/hp".equals(name)) {
            l.onHealChange(new MeterEvent(MeterEvent.Type.HP, values));
        } else if ("gfx/hud/meter/nrj".equals(name)) {
            l.onStaminaChange(new MeterEvent(MeterEvent.Type.STAMINA, values));
        } else if ("gfx/hud/meter/hngr".equals(name)) {
            l.onHungerChange(new MeterEvent(MeterEvent.Type.HUNGER, values));
        } else if ("gfx/hud/meter/happy".equals(name)) {
            l.onHappinessChange(new MeterEvent(MeterEvent.Type.HAPINESS, values));
        } else if ("gfx/hud/meter/auth".equals(name)) {
            l.onAuthorityChange(new MeterEvent(MeterEvent.Type.AUTHORITY, values));
        }
    }

    private void onImgChange(TaskListener l, int id, String res) {
        if (id == -666) {
            l.onTaskProgress(new TaskEvent(Integer.parseInt(res)));
        }
    }

    private void onImgDestroy(TaskListener l, int id) {
        if (id == -666) {
            TaskEvent e = new TaskEvent(100);
            l.onTaskProgress(e);
            l.onTaskComplete(e);
        }
    }

    private void onCursChange(CursorListener l, String res) {
        Matcher m;
        if ((m = cursorName.matcher(res)).matches()) {
            res = m.group(1);
        }
        l.onCursorChange(new CursorEvent(res));
    }

    private void onWidgetCreate(WidgetListener<?> l, Widget wdg) {
        if (Config.getEnableDebugMode()) System.out.println(wdg);

        Class<?> c = l.getInterest();
        if (c.isInstance(wdg)) {
            l.onCreate(new haven.event.WidgetEvent(haven.event.WidgetEvent.Type.CREATE, wdg));
        }
    }

    private void onMapClick(ClickListener l, Coord coord, int button, int modflags) {
        l.onMapClick(new ClickEvent(coord, button, modflags));
    }

    private void onMapClick(ClickListener l, Gob gob,int button, int modflags) {
        l.onMapClick(new ClickEvent(gob, button, modflags));
    }

    private void onCombatCreate(CombatListener cl, Widget wdg){
        if (Config.getEnableDebugMode()) System.out.println("Combat initiated: " + wdg);

        cl.onCreate(new haven.event.WidgetEvent(haven.event.WidgetEvent.Type.CREATE, wdg));
    }

    private void onCombatDestroy(CombatListener cl, Widget wdg){
        if (Config.getEnableDebugMode()) System.out.println("Combat Destroyed: " + wdg);

        cl.onDestroy(new haven.event.WidgetEvent(WidgetEvent.Type.DESTROY, wdg));
    }

    private void onAttackDone(CombatListener cl, Widget wdg){
        if(wdg instanceof Fightsess) {
            System.out.println("Attack tried");
            cl.onAttack();
        }
    }



    static {
        Console.setscmd("scstart", (cons, args) -> {
            if (args.length < 1) {
                System.out.println("No scriptname given.");
            }
            MaidEngine.getInstance().doTask(args[1]);
        });

        Console.setscmd("scstop", (cons, args) -> {
            MaidEngine.getInstance().stopTask();
        });
    }
}
