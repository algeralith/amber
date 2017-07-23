package haven;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.transform.ThreadInterrupt;
import groovy.util.GroovyScriptEngine;
import groovy.util.ResourceException;
import groovy.util.ScriptException;
import haven.automation.AreaSelectCallback;
import haven.pathfinder.PFListener;
import haven.pathfinder.Pathfinder;
import haven.event.*;

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
    private Thread task, wait;
    //public HavenPanel havenPanel;
    private TaskListener taskListener;
    private CursorListener cursorListener;
    private MeterListener meterListener;
    private ItemListener itemListener;
    private WidgetListener<?> widgetListener;
    private WindowListener windowListener;
    private ClickListener clickListener;
    private CombatListener combatListener;
    private haven.pathfinder.PFListener pfListener;
    private Pathfinder pf;
    public Thread pfthread;

    public HashMap<String, Object> globalStore = new HashMap<String, Object>();

    public int stamina = 100;
    public int energy = 4000;

    private int menuGridId = 0;

    //Error handling
    public boolean errored = false;
    public String error = null;

    public Maid() {

        binding = new Binding();

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
            doErr("Can't open scripts folder. I will try creating it...");

            boolean success = new File(scripts_folder).mkdir();

            if (success) {
                try {
                    gse = new GroovyScriptEngine(scripts_folder);
                } catch (IOException e2) {
                   doErr("Directory \"" + scripts_folder + "\" gives errors. I give up.");

                    throw new RuntimeException("Can't initialize groovy script engine.", e2);
                }
            } else {
                doErr("Can't read/create \"" + scripts_folder + "\".");

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



    public void preProcessing() {
    }

    public void postProcessing() {
        clearListeners();
    }

    public void sleep() throws InterruptedException {
        synchronized (LOCK) {
            LOCK.wait();
        }
    }

    public void wakeup() {
        synchronized (LOCK) {
            LOCK.notify();
        }
    }

    public String waitForCursor() throws InterruptedException {
        final String[] retval = new String[1];

        cursorListener = new CursorListener() {

            public void onCursorChange(CursorEvent e) {
                retval[0] = e.getName();
                wakeup();
            }
        };

        sleep();

        cursorListener = null;

        return retval[0];
    }

    public GItem waitForGrab() throws InterruptedException {
        final GItem[] retval = new GItem[1];
        itemListener = new ItemAdapter() {

            @Override
            public void onItemGrab(ItemEvent e) {
                retval[0] = e.getItem();

                wakeup();
            }
        };

        sleep();

        itemListener = null;

        return retval[0];
    }

    public GItem waitForRelease() throws InterruptedException {
        final GItem[] retval = new GItem[1];
        itemListener = new ItemAdapter() {

            @Override
            public void onItemRelease(ItemEvent e) {
                retval[0] = e.getItem();

                wakeup();
            }
        };

        sleep();

        itemListener = null;

        return retval[0];
    }

    public GItem waitForItemChange() throws InterruptedException {
        final GItem[] retval = new GItem[1];
        itemListener = new ItemAdapter() {

            @Override
            public void onItemChange(ItemEvent e) {
                retval[0] = e.getItem();

                wakeup();
            }

            @Override
            public void onItemCreate(ItemEvent e) {
                retval[0] = e.getItem();

                wakeup();
            }

            @Override
            public void onItemDestroy(ItemEvent e) {
                retval[0] = e.getItem();

                wakeup();
            }

            @Override
            public void onItemRelease(ItemEvent e) {
                retval[0] = e.getItem();

                wakeup();
            }
        };

        sleep();

        itemListener = null;

        return retval[0];
    }

    public GItem waitForItemCreate() throws InterruptedException {
        final GItem[] retval = new GItem[1];
        itemListener = new ItemAdapter() {

            @Override
            public void onItemCreate(ItemEvent e) {
                retval[0] = e.getItem();

                wakeup();
            }
        };

        sleep();

        itemListener = null;

        return retval[0];
    }

    public GItem waitForItemDestroy() throws InterruptedException {
        final GItem[] retval = new GItem[1];
        itemListener = new ItemAdapter() {

            @Override
            public void onItemDestroy(ItemEvent e) {
                retval[0] = e.getItem();

                wakeup();
            }
        };

        sleep();

        itemListener = null;

        return retval[0];
    }

    public Window waitForWindowCreate() throws InterruptedException {
        final Window[] retval = new Window[1];
        windowListener = new WindowAdapter() {
            @Override
            public void onWindowCreate(WindowEvent e) {
                retval[0] = e.getWindow();
                wakeup();
            }
        };
        sleep();
        windowListener = null;
        return retval[0];
    }

    public Window waitForWindowDestroy() throws InterruptedException {
        final Window[] retval = new Window[1];
        windowListener = new WindowAdapter() {
            @Override
            public void onWindowDestroy(WindowEvent e) {
                retval[0] = e.getWindow();
                wakeup();
            }
        };
        sleep();
        windowListener = null;
        return retval[0];
    }

    public void waitForTask() throws InterruptedException {
        taskListener = new TaskAdapter() {

            @Override
            public void onTaskComplete(TaskEvent taskEvent) {
                wakeup();
            }
        };

        sleep();

        taskListener = null;
    }

    public FlowerMenu waitForFlowerMenu() throws InterruptedException {
        final FlowerMenu retval[] = new FlowerMenu[1];

        widgetListener = new WidgetListener<FlowerMenu>() {

            public Class<FlowerMenu> getInterest() {
                return FlowerMenu.class;
            }

            public void onCreate(haven.event.WidgetEvent<FlowerMenu> e) {
                retval[0] = e.getWidget();

                wakeup();
            }

            public void onDestroy(haven.event.WidgetEvent<FlowerMenu> e) {
            }
        };

        sleep();

        widgetListener = null;

        return retval[0];
    }

    public Widget waitForWidget() throws InterruptedException {
        final Widget retval[] = new Widget[1];
        widgetListener = new WidgetListener<Widget>() {

            public Class<Widget> getInterest() {
                return Widget.class;
            }

            public void onCreate(WidgetEvent<Widget> e) {
                retval[0] = e.getWidget();

                wakeup();
            }

            public void onDestroy(WidgetEvent<Widget> e) {
            }
        };

        sleep();

        widgetListener = null;

        return retval[0];
    }

    public Widget waitForCombat() throws InterruptedException {
        final Widget retval[] = new Widget[1];
        combatListener = new CombatListener<Widget>(){
            public Class<Widget> getInterest() {
                return Widget.class;
            }

            public void onCreate(WidgetEvent<Widget> e) {
                retval[0] = e.getWidget();

                System.out.println("FS created");
                wakeup();
            }

            public void onDestroy(WidgetEvent<Widget> e) {
                combatListener = null;
            }

            public void onAttack(){
                System.out.println("Attack done");
                wakeup();
            }
        };

        Widget fs = getWidget(Fightsess.class);
        if(fs != null) {
            System.out.println("FS already exists..");
            return fs;
        }
        sleep();

        //combatListener = null;

        return retval[0];
    }

    public void waitForAttack() throws InterruptedException{
        if(combatListener != null){
            sleep();
        }
    }

    public Map<String, Object> waitForClick() throws InterruptedException {
        final HashMap<String, Object> retval = new HashMap<String, Object>(4);
        clickListener = new ClickAdapter() {
            public void onMapClick(ClickEvent e) {
                if (e.getType().equals(ClickEvent.Type.COORD)) {
                    retval.put("type", e.getType());
                    retval.put("coord", e.getCoord());
                    retval.put("button", e.getButton());
                    retval.put("modflags", e.getModflags());
                } else if (e.getType().equals(ClickEvent.Type.GOB)) {
                    retval.put("type", e.getType());
                    retval.put("gob", e.getGob());
                    retval.put("button", e.getButton());
                    retval.put("modflags", e.getModflags());
                }
                wakeup();
            }
        };

        sleep();
        clickListener = null;
        return retval;
    }

    // TODO: Don't rely on sleeping
    public void waitForMovementStop(Closure func) throws InterruptedException {
        if (Config.getEnableDebugMode()) doSay("waiting for movement to stop");
        boolean moving = isMoving(getPlayer());
        while (!moving) {
            Thread.sleep(100);
            if (func != null) func.call();
            moving = isMoving(getPlayer());
        }
        while (moving) {
            Thread.sleep(100);
            if (func != null) func.call();
            moving = isMoving(getPlayer());
        }

    }

    public void waitForMovementStop() throws InterruptedException {
        waitForMovementStop(null);
    }

    public void doLogout() {
        getUI().sess.close();
    }

    public Coord getScreenCenter() {
        Coord sc = new Coord(
                (int) Math.round(Math.random() * 200 + getUI().root.sz.x / 2 - 100),
                (int) Math.round(Math.random() * 200 + getUI().root.sz.y / 2 - 100));
        return sc;
    }

    //TODO ::
    /*
    public String getCursorName() {
        return getUI().root.getcurs(havenPanel.mousepos).basename();
    }
    */

    public Coord c3dc22dc(Coord3f c3d) {
        return new Coord((int)(c3d.x), (int)(c3d.y));
    }

    public Coord3f getCoord(Gob gob) {
        if (gob != null) {
            return gob.getc();
        } else {
            return null;
        }
    }

    //TODO ::
    public Coord get2DCoord(Gob gob) {
        if (gob != null) {
            return get2DCoord(gob.getc());
        } else {
            return null;
        }
    }

    public Coord get2DCoord(Coord3f coord) {
        return new Coord((int)coord.x, (int)coord.y);
    }

    public Coord get2DCoord() {
        return get2DCoord(getPlayer());
    }

    public Coord2d getCoord2d(Coord3f coord)
    {
        return new Coord2d((double)coord.x, (double)coord.y);
    }

    public Gob getGob(long id) {
        synchronized (getUI().sess.glob.oc) {
            return getUI().sess.glob.oc.getgob(id);
        }
    }


    public Gob getPlayer() {
        return getGUI().map.player();
    }

    public Coord3f getCoord() {
        return getCoord(getPlayer());
    }

    public Coord[] selectArea() throws InterruptedException {

        final Coord[] coords = new Coord[2];

        //register a call back -- This creates the selector
        getGUI().map.registerAreaSelect(new AreaSelectCallback() {
            @Override
            public void areaselect(Coord a, Coord b) {
                coords[0] = a;
                coords[1] = b;

                //For some reason, Romov's code does not seem to destroy when using callback
                if (getGUI().map.selection != null) {
                    getGUI().map.selection.destroy();
                    getGUI().map.selection = null;
                }

                getGUI().map.unregisterAreaSelect();

                wakeup();
            }
        });

        sleep();

        return new Coord[]{coords[0], coords[1]};
    }

    //TODO ::
    public void pfLeftClick(Coord mc, String action) throws InterruptedException {
        Gob player = getGUI().map.player();
        if (player == null)
            return;

        synchronized (Pathfinder.class) {
            if (pf != null) {
                pf.terminate = true;
                pfthread.interrupt();
                // cancel movement
                if (player.getattr(Moving.class) != null)
                    getGUI().map.wdgmsg("gk", 27);
            }

            Coord src = player.rc.floor();
            int gcx = haven.pathfinder.Map.origin - (src.x - mc.x);
            int gcy = haven.pathfinder.Map.origin - (src.y - mc.y);
            if (gcx < 0 || gcx >= haven.pathfinder.Map.sz || gcy < 0 || gcy >= haven.pathfinder.Map.sz)
                return;

            pfListener = new PFListener() {
                @Override
                public void pfDone(Pathfinder thread) {
                    wakeup();
                }
            };


            pf = new Pathfinder(getGUI().map, new Coord(gcx, gcy), action);
            pf.addListener(pfListener);
            pfthread = new Thread(pf, "Pathfinder");
            pfthread.start();

            sleep();
            pfListener = null;
        }
    }

    // TODO: Check if this works for sprites
    public Resource getResource(Gob g) {
        Resource res = null;

        ResDrawable rd;

        if ((rd = g.getattr(ResDrawable.class)) != null) {
            res = rd.res.get();
        }

        return res;
    }

    public String getName(Gob g, boolean v) {
        String name = null;
        try {
            Resource res = g.getres();

            if (res != null) {
                name = res.name;
            } else if (g.id > 0) {
                if (v) doErr("Resource missing for gob " + g.id);
            }
        } catch (Exception e) {} // should maybe be caught in a better fashion

        return name;
    }

    public String getName(Gob g) {
        return getName(g, false);
    }

    public boolean isMoving(Gob g) {
        Moving mv = g.getattr(Moving.class);
        if (mv == null)
            return false;
        else
            return true;
    }

    public boolean isPlayer(Gob gob) {
        return (gob.type == Gob.Type.PLAYER);
    }

    //TODO :: Fix isEnemy. Still debating whether I even want it or not
    /*
    public boolean isEnemy(Gob gob) {
        KinInfo kininfo = gob.getattr(KinInfo.class);
        return (kininfo == null || kininfo.group == 2) && !gob.isplayer() && gob.doneLoading && !gob.isDeadHuman;
    }
    */

    public <C> C getWidget(Class<C> klass) {
        for (Widget w : getUI().rwidgets.keySet()) {

            if (klass.isInstance(w)) {
                return klass.cast(w);
            }
        }
        return null;
    }

    public <C> C[] getWidgets(Class<C> klass) {
        List<C> widgets = new ArrayList<C>();
        for (Widget w : getUI().rwidgets.keySet()) {

            if (klass.isInstance(w)) {
                widgets.add(klass.cast(w));
            }
        }
        return widgets.toArray((C[]) Array.newInstance(klass, widgets.size()));
    }

    public ArrayList<Widget> getWidgets(String name) {
        ArrayList<Widget> widgets = new ArrayList<Widget>();
        for (Widget w : getUI().rwidgets.keySet()) {
            if (w.getClass().getName().equals(name)) {
                widgets.add(w);
            }
        }
        return widgets;
    }

    public Widget getWidget(String name) {
        for (Widget w : getUI().rwidgets.keySet()) {
            if (w.getClass().getName().equals(name)) {
                return w;
            }
        }
        return null;
    }

    // Doesn't work - returns null - always
    public Inventory getInventory(String name) {
        for (Widget wdg = getUI().root.child; wdg != null; wdg = wdg.next) {
            if (wdg instanceof Window) {
                Window window = (Window) wdg;
                if (window.cap != null && window.cap.text.equalsIgnoreCase(name)) {
                    for (Widget w = wdg.child; w != null; w = w.next) {
                        if (w instanceof Inventory) {
                            return (Inventory) w;
                        }
                    }
                }
            }
        }
        return null;
    }

    public Inventory getInventory() {
        return getGUI().maininv;
    }

    public int getMaxSpace(Inventory inv) {
        return inv.isz.x*inv.isz.y;
    }

    public int getSpaceLeft(Inventory inv) {
        int maxspace = getMaxSpace(inv);
        int spaceleft = maxspace;
        for (GItem item : inv.wmap.keySet()) {
            spaceleft -= getSize(item);
        }
        return spaceleft;
    }

    public int getSize(GItem item) {
        try {
            Coord sz = item.sprite().sz().div(32);
            return sz.x * sz.y;
        } catch (Loading l) {
            return 0;
        }
    }

    public int getSize(WItem item) {
        return getSize(item.item);
    }

    public WItem getDragItem() {
        return getGUI().vhand;
    }

    public int getMeter(Buff b) {
        return b.ameter;
    }

    public int getTimeLeft(Buff b) {
        if (b.cmeter >= 0) {
            long now = System.currentTimeMillis();
            double m = b.cmeter / 100.0;
            if (b.cticks >= 0) {
                double ot = b.cticks * 0.06;
                double pt = ((double) (now - b.gettime)) / 1000.0;
                m *= (ot - pt) / ot;
            }
            return (int) Math.round(m * 100);
        }
        return 0;
    }

    public String getName(Buff b) {
        Resource r = b.res.get();

        if (r == null) {
            return "";
        }

        Resource.Tooltip tt = r.layer(Resource.tooltip);

        if (tt == null) {
            return "";
        }

        return tt.t;
    }

    public void doAction(String msg, Object... args) {
        if (menuGridId != 0) {
            getUI().rcvr.rcvmsg(menuGridId, msg, args);
        } else {
            doErr("menuGrid not identified");
        }
    }

    public void doInteract(Coord mc, int modflags) {
        Coord rc = new Coord2d(mc.x, mc.y).floor(OCache.posres);
        getGUI().map.wdgmsg("itemact", getScreenCenter(), rc, modflags);
    }

    public void doInteract(Coord mc) {
        doInteract(mc, 0);
    }

    public void doPort() {
        doAction("act", "travel", "hearth");
    }

    // Not tested
    public void doClick(Coord mc, int button, int modflags) {
        Coord rc = new Coord2d(mc.x, mc.y).floor(OCache.posres);
        getGUI().map.wdgmsg("click", getScreenCenter(), rc, button, modflags);
    }

    public void doClick(Coord mc, int button) {
        doClick(mc, button, 0);
    }

    public void doLeftClick(Coord mc, int modflags) {
        doClick(mc, 1, modflags);
    }

    public void doLeftClick(Coord mc) {
        doClick(mc, 1, 0);
    }

    public void doRightClick(Coord mc, int modflags) {
        doClick(mc, 3, modflags);
    }

    public void doRightClick(Coord mc) {
        doClick(mc, 3, 0);
    }

    public void doClick(Gob gob, int button, int modflags) {
        Coord sc = getScreenCenter();
        //Pre-Floating points
        //Coord oc = gob.getc().getCoord();
        Coord oc = getCoord2d(gob.getc()).floor(OCache.posres);
        //havenPanel.ui.root.wdgmsg("click", sc, oc, button, modflags, (int)gob.id, oc);
        // TODO: Fix magic numbers
        getGUI().map.wdgmsg("click", sc, oc, button, modflags, 0, (int)gob.id, oc, 0, 0);
    }

    public void doClick(Gob gob, int button) {
        doClick(gob, button, 0);
    }

    public void doLeftClick(Gob gob, int modflags) {
        doClick(gob, 1, modflags);
    }

    public void doLeftClick(Gob gob) {
        doClick(gob, 1, 0);
    }

    public void doRightClick(Gob gob, int modflags) {
        doClick(gob, 3, modflags);
    }

    public void doRightClick(Gob gob) {
        doClick(gob, 3, 0);
    }

    public void doInteract(Gob gob, int modflags) {
        Coord sc = getScreenCenter();
        Coord3f oc = gob.getc();
        getUI().root.wdgmsg("itemact", sc, oc, modflags, (int)gob.id, oc);
    }

    public void doInteract(Gob gob) {
        doInteract(gob, 0);
    }

    public void doPlaceAll(String name, Coord c, int angle) {
        WItem item = getItemFromInventory(name);
        if (item != null) {
            item.mousedown(Coord.z, 1);
            try {
                waitForGrab();
                doInteract(c, 0);
                waitForRelease();
                getGUI().map.wdgmsg("place", c, angle, 1, 5);
            } catch (Exception e) {
            }
        }
    }

    public void doPlaceAll(Coord c, int angle) {
        try {
            doInteract(c, 0);
            waitForRelease();
            getGUI().map.wdgmsg("place", c, angle, 1, 5);
        } catch (Exception e) {}
    }

    public void doPlaceAll(Coord c) {
        doPlaceAll(c, 0);
    }

    public void doPlaceAll(String name, Coord c) {
        doPlaceAll(name, c, 0);
    }

    // Purely aesthetic
    public void doShowInventory() {
        if (getGUI().invwnd != null) getGUI().invwnd.show();
    }

    public Gob[] doAreaFind(Coord coord, double radius, String name) {
        List<Gob> list = new LinkedList<Gob>();
        double max = toTile(radius);

        synchronized (getUI().sess.glob.oc) {
            for (Gob gob : getUI().sess.glob.oc) {
                String gobName = getName(gob);

                if (gobName != null && gobName.contains(name)) {
                    double dist = get2DCoord(gob.getc()).dist(coord);
                    if (dist <= max) {
                        list.add(gob);
                    }
                }
            }
        }

        return list.toArray(new Gob[list.size()]);
    }

    public Gob[] doAreaFind(int offsetx, int offsety, double radius, String name) {
        Coord coord = get2DCoord().add(toTile(offsetx), toTile(offsety));

        return doAreaFind(coord, radius, name);
    }

    public Gob[] doAreaFind(double radius, String name) {
        return doAreaFind(0, 0, radius, name);
    }

    public Gob[] doAreaList(Coord coord, double radius) {
        List<Gob> list = new LinkedList<Gob>();

        double max = toTile(radius);

        synchronized (getUI().sess.glob.oc) {
            for (Gob gob : getUI().sess.glob.oc) {
                double dist = get2DCoord(gob.getc()).dist(coord);
                if (dist <= max) {
                    list.add(gob);
                }
            }
        }
        return list.toArray(new Gob[list.size()]);
    }

    public Gob[] doAreaList(int offsetx, int offsety, double radius) {
        Coord coord = get2DCoord().add(toTile(offsetx), toTile(offsety));

        return doAreaList(coord, radius);
    }

    public Gob[] doAreaList(double radius) {
        return doAreaList(0, 0, radius);
    }

    public String[] doList(FlowerMenu menu) {
        String names[] = new String[menu.opts.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = menu.opts[i].name;
        }
        return names;
    }

    public boolean doSelect(FlowerMenu menu, String option) {
        for (int i = 0; i < menu.opts.length; i++) {
            if (option.equalsIgnoreCase(menu.opts[i].name)) {
                menu.wdgmsg(menu, "cl", menu.opts[i].num, 1);
                return true;
            }
        }
        return false;
    }

    public boolean isWaterContainer(String resname) {
        return (resname.equals("gfx/invobjs/waterflask") ||
                resname.equals("gfx/invobjs/waterskin") ||
                resname.equals("gfx/invobjs/small/bucket-water"));
    }

    public String getResource(GItem item) {
        try {
            return resname(item.resource());
        } catch (Exception e) {
            return "";
        }
    }

    private String resname(Resource res){
        if(res != null){
            return res.name;
        }
        return "";
    }

    public String getResource(WItem item) {
        return getResource(item.item);
    }

    public UI getUI() {
        return HavenPanel.lui;
    }

    public GameUI getGUI() {
        return HavenPanel.lui.root.findchild(GameUI.class);
    }

    public Equipory getEquipory() {
        return getGUI().getequipory();
    }

    public WItem getItemFromWmap(String resn, Map<GItem, WItem[]> wmap) {
        for (WItem[] value : wmap.values()) {
            for (WItem wItem : value) {
                String resname = getResource(wItem);
                if (resname.contains(resn)) {
                    return wItem;
                }
            }
        }
        return null;
    }

    public WItem getItemFromEquipory(String resn) {
        return getItemFromWmap(resn, getEquipory().wmap);
    }

    public WItem getItemFromEquipory(int slot) {
        return getEquipory().quickslots[slot];
    }

    public WItem getItem(String resn, int order) {
        WItem item;
        if (order == 0) {
            item = getItemFromEquipory(resn);
            if (item != null)
                return item;
            return getItemFromInventory(resn);
        } else {
            item = getItemFromInventory(resn);
            if (item != null)
                return item;
            return getItemFromEquipory(resn);
        }
    }

    public WItem getItem(String resn) {
        return getItem(resn, 0);
    }

    public WItem getItemFromInventory(String resn) {
        return getItemFromWmap(resn, convertWmap(getItemsFromInventory()));
    }

    public Map<GItem, WItem> getItemsFromInventory() {
        return getInventory().wmap;
    }

    public Collection<WItem> getWItemsFromInventory() {
        return getItemsFromInventory().values();
    }

    public boolean doDrinkFromWmap(Map<GItem, WItem[]> wmap) {
        boolean ret = false;
        if (stamina == 100) return ret;
        for (WItem[] value : wmap.values()) {
            for (WItem wItem : value) {
                String resname = getResource(wItem);
                if (isWaterContainer(resname)) {
                    if (hasContents(wItem.item)) {
                        wItem.mousedown(getScreenCenter(), 3);
                        try {
                            FlowerMenu fm = waitForFlowerMenu();
                            doSelect(fm, "Drink");
                            waitForTask();
                            ret = true;
                            if (stamina == 100) return ret;
                        } catch (Exception e) {}
                    }
                }
            }
        }
        return ret;
    }

    public boolean hasContents(GItem item) {
        for (ItemInfo info : item.info()) {
            if (info instanceof ItemInfo.Contents)
                return true;
        }
        return false;
    }

    public boolean doDrinkFromEquipory() {
        return doDrinkFromWmap(getEquipory().wmap);
    }

    public boolean doDrinkFromInventory() {
        return doDrinkFromWmap(convertWmap(getInventory().wmap));
    }

    public boolean doDrink() {
        if (stamina == 100) return false;
        boolean inv = doDrinkFromInventory();
        boolean eq = doDrinkFromEquipory();
        return (inv || eq);
    }

    public boolean doUnequip(String resn) {
        WItem item = getItemFromEquipory(resn);
        if (item != null) {
            doTransfer(item);
            return true;
        }
        return false;
    }

    public void doTransfer(GItem item) {
        item.wdgmsg("transfer", getScreenCenter());
    }

    public void doTransfer(WItem item) {
        doTransfer(item.item);
    }

    public void doScrollTransfer(Widget from, Widget to, int amount) {
        from.wdgmsg("invxf", to.wdgid(), amount);
    }

    public void doTake(GItem item) {
        item.wdgmsg("take", new Coord(0,0));
    }

    public void doTake(WItem item) {
        doTake(item.item);
    }

    public void doDrop(Widget widget, int n) {
        widget.wdgmsg("drop", n);
    }

    public void doEquipDragging() {
        doDrop(getEquipory(), -1);
    }

    public void doDestroy(Gob gob) {
        getGUI().wdgmsg("act", "destroy");
        doLeftClick(gob);
        try {
            waitForTask();

        } catch (Exception e) {}
        doRightClick(getScreenCenter());
    }

    // Can use quickslots instead of using this convoluted function
    public Map<GItem, WItem[]> convertWmap(Map<GItem, WItem> oldwmap) {
        Map<GItem, WItem[]> wmap = new HashMap<GItem, WItem[]>();
        Inventory inventory = getInventory();
        for (GItem item : oldwmap.keySet()) {
            WItem[] wItems = new WItem[1];
            wItems[0] = oldwmap.get(item);
            wmap.put(item, wItems);
        }
        return wmap;
    }

    public static int makeFlags(boolean shift, boolean ctrl, boolean alt, boolean meta) {
        int flags = 0;
        if (shift) {
            flags |= 1;
        }
        if (ctrl) {
            flags |= 2;
        }
        if (alt) {
            flags |= 4;
        }
        if (meta) {
            flags |= 8;
        }
        return flags;
    }

    public static int toTile(int i) {
        return i * MCache.tilesz2.x;
    }

    public static double toTile(double i) {
        return i * MCache.tilesz.x;
    }

    public static Coord toTile(Coord coord) {
        Coord c = new Coord(coord);
        c = c.div(MCache.tilesz2);
        c = c.mul(MCache.tilesz2);
        c = c.add(MCache.tilesz2.div(2));
        return (c);
    }

    public double getDistance(Coord c1, Coord c2) {
        double dx = c2.x - c1.x;
        double dy = c2.y - c1.y;
        return Math.sqrt((dx * dx) + (dy * dy));
    }

    void setMenuGridId(int menuGridId) {
        this.menuGridId = menuGridId;
    }

    public CursorListener getCursorListener() {
        return cursorListener;
    }

    public void setCursorListener(CursorListener cursorListener) {
        this.cursorListener = cursorListener;
    }

    public ItemListener getItemListener() {
        return itemListener;
    }

    public void setItemListener(ItemListener itemListener) {
        this.itemListener = itemListener;
    }

    public WindowListener getWindowListener() {
        return windowListener;
    }

    public void setWindowListener(WindowListener windowListener) {
        this.windowListener = windowListener;
    }

    public MeterListener getMeterListener() {
        return meterListener;
    }

    public void setMeterListener(MeterListener meterListener) {
        this.meterListener = meterListener;
    }

    public TaskListener getTaskListener() {
        return taskListener;
    }

    public void setTaskListener(TaskListener taskListener) {
        this.taskListener = taskListener;
    }

    public WidgetListener<?> getWidgetListener() {
        return widgetListener;
    }

    public void setWidgetListener(WidgetListener<?> widgetListener) {
        this.widgetListener = widgetListener;
    }

    public void setClickListener(ClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public ClickListener getClickListener() {
        return clickListener;
    }

    public void setCombatListener(CombatListener combatListener){
        this.combatListener = combatListener;
    }

    public CombatListener getCombatListener (){
        return combatListener;
    }

    void clearListeners() {
        cursorListener = null;
        itemListener = null;
        windowListener = null;
        meterListener = null;
        taskListener = null;
        widgetListener = null;
        clickListener = null;
        combatListener = null;
        pfListener = null;
    }

    public String getError() {
        if (errored == true) {
            errored = false;

            if (error != null) {
                String er = error;
                error = null;
                return er;
            }
            else
                return "Unknown Error";
        }

        return null;
    }
}
