package com.yuyu.bongocat;

import com.google.gson.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** 用 Rive 自己的无头运行器验证真实 RML 状态机。 */
public class RiveStateMachineTest {
    private static File cli;
    private static Process engine;
    private static BufferedWriter input;
    private static final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
    private static final StringBuffer transcript = new StringBuffer();
    private static final File ROOT = new File(System.getProperty("bongocat.projectDir", "."));
    private static final String[] ACTIONS = {"Blink", "Lick", "Tail", "Stretch", "Yawn", "Look", "Crawl"};
    private static final int[] FRAMES = {66, 204, 180, 216, 180, 192, 228};

    @BeforeClass public static void findCli() throws Exception {
        String configured = System.getenv("RIVE_CLI");
        cli = configured == null ? new File(System.getProperty("user.home"),
                ".rive/bin/" + (System.getProperty("os.name").contains("Windows") ? "rive.exe" : "rive")) : new File(configured);
        assumeTrue("安装 Rive CLI 或设置 RIVE_CLI，才能运行模型引擎测试", cli.isFile());
        // 共用一个原生进程，每个场景重新创建画板，避免重复启动 CLI。
        engine = new ProcessBuilder(cli.getAbsolutePath(), "rive", "--commands").directory(ROOT).redirectErrorStream(true).start();
        input = new BufferedWriter(new OutputStreamWriter(engine.getOutputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(() -> {
            try (BufferedReader output = new BufferedReader(new InputStreamReader(engine.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = output.readLine()) != null) { transcript.append(line).append('\n'); replies.offer(line); }
            } catch (IOException exception) { replies.offer("err " + exception); }
        }, "rive-test-output");
        reader.setDaemon(true); reader.start();
        execute("help");
    }

    private static void execute(String command) throws Exception {
        input.write(command); input.newLine(); input.flush();
        String acknowledgement = "ok " + command.split(" ", 2)[0];
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < until) {
            String line = replies.poll(1, TimeUnit.SECONDS);
            if (line != null) {
                assertFalse(transcript.toString(), line.startsWith("err "));
                if (line.startsWith(acknowledgement)) return;
            } else if (!engine.isAlive()) fail("Rive 提前退出：" + engine.exitValue() + "\n" + transcript);
        }
        engine.destroyForcibly(); fail("Rive 命令超时：" + command);
    }

    @AfterClass public static void closeEngine() throws Exception {
        if (engine == null) return;
        try {
            if (engine.isAlive()) { execute("quit"); assertTrue("Rive 未正常退出", engine.waitFor(5, TimeUnit.SECONDS)); }
            assertEquals(transcript.toString(), 0, engine.exitValue());
        } finally { if (engine.isAlive()) engine.destroyForcibly(); }
    }

    private Map<String, JsonElement> run(String name, String... steps) throws Exception {
        File directory = new File(ROOT, "build/rive-tests");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File json = new File(directory, name + ".json");
        File log = new File(directory, name + ".log");
        int start = transcript.length();
        try {
            execute("artboard BongoCat");
            for (String step : steps) {
                int equals = step.indexOf('=');
                execute(step.substring(2, equals) + " " + step.substring(equals + 1));
            }
            execute("data-dump " + json.getAbsolutePath().replace('\\', '/'));
        } finally {
            Files.write(log.toPath(), transcript.substring(start).getBytes(StandardCharsets.UTF_8));
        }
        JsonObject root = JsonParser.parseString(new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("PetStateMachine", root.get("stateMachine").getAsString());
        Map<String, JsonElement> values = new HashMap<>();
        for (JsonElement p : root.getAsJsonObject("viewModel").getAsJsonArray("properties")) {
            JsonObject field = p.getAsJsonObject();
            values.put(field.get("path").getAsString(), field.get("value"));
        }
        return values;
    }

    private String[] force(String action, String... steps) {
        List<String> all = new ArrayList<>();
        for (String name : ACTIONS) if (!name.equals(action)) all.add("--data=seen" + name + "=true");
        all.addAll(Arrays.asList(steps));
        return all.toArray(new String[0]);
    }

    @Test public void changingArtboardClearsThePreviousScenario() throws Exception {
        run("isolation-dirty", "--data=holdSign=true", "--data=seenBlink=true", "--data=bodyColor=FF707070", "--advance=120s");
        Map<String, JsonElement> clean = run("isolation-clean");
        assertEquals(0, clean.get("state").getAsInt());
        assertEquals(0, clean.get("inactive").getAsDouble(), .001);
        assertFalse(clean.get("holdSign").getAsBoolean());
        assertFalse(clean.get("seenBlink").getAsBoolean());
        assertEquals("FFFFFFFF", clean.get("bodyColor").getAsString());
    }

    @Test public void sevenActionsAreReachableAndHaveFiniteDurations() throws Exception {
        for (int i = 0; i < ACTIONS.length; i++) {
            Map<String, JsonElement> active = run(ACTIONS[i], force(ACTIONS[i], "--advance=120"));
            assertEquals(ACTIONS[i], i + 1, active.get("state").getAsInt());
            assertTrue(active.get("seen" + ACTIONS[i]).getAsBoolean());
            Map<String, JsonElement> ended = run(ACTIONS[i] + "-end", force(ACTIONS[i], "--advance=" + (90 + FRAMES[i] + 3)));
            assertEquals(ACTIONS[i], i == 6 ? 8 : 0, ended.get("state").getAsInt());
        }
    }

    @Test public void drowsyStartsAfter90SecondsAndSleepAfterAnother6() throws Exception {
        assertTrue(run("before90", "--advance=5399").get("state").getAsInt() < 9);
        assertEquals(9, run("after90", "--advance=5401").get("state").getAsInt());
        assertEquals(9, run("before96", "--advance=5758").get("state").getAsInt());
        assertEquals(10, run("after96", "--advance=5762").get("state").getAsInt());
        assertEquals(10, run("stayAsleep", "--advance=120s").get("state").getAsInt());
    }

    @Test public void oneTapWakesInNineTenthsOfASecondAndRestartsInactivity() throws Exception {
        assertEquals(14, run("wake-start", "--advance=100s", "--data=interaction=1", "--advance=51").get("state").getAsInt());
        Map<String, JsonElement> awake = run("wake-end", "--advance=100s", "--data=interaction=1", "--advance=55");
        assertEquals(0, awake.get("state").getAsInt());
        assertTrue(awake.get("inactive").getAsDouble() < 1);
        assertEquals(14, run("wake-drowsy", "--advance=92s", "--data=interaction=1").get("state").getAsInt());
    }

    @Test public void restingAfterCrawlEntersSleepAndWakesNormally() throws Exception {
        assertEquals(9, run("prone-drowsy", "--data=playLieCrawl=1", "--advance=5401").get("state").getAsInt());
        assertEquals(10, run("prone-sleep", "--data=playLieCrawl=1", "--advance=5762").get("state").getAsInt());
        assertEquals(0, run("prone-sleep-awake", "--data=playLieCrawl=1", "--advance=100s", "--data=interaction=1", "--advance=55").get("state").getAsInt());
    }

    @Test public void crawlStopsLiesAndCanBeInterrupted() throws Exception {
        Map<String, JsonElement> crawl = run("crawl-progress", force("Crawl", "--advance=180"));
        assertEquals(7, crawl.get("state").getAsInt());
        assertTrue(crawl.get("crawlProgress").getAsDouble() > 0.3);
        assertTrue(crawl.get("crawlProgress").getAsDouble() < 0.5);
        assertEquals(8, run("lie-holds", force("Crawl", "--advance=30s")).get("state").getAsInt());
        assertEquals(8, run("crawl-stop", force("Crawl", "--advance=2s", "--data=stopCrawl=1", "--advance=2")).get("state").getAsInt());
        assertEquals(14, run("lie-tap", force("Crawl", "--advance=10s", "--data=interaction=1")).get("state").getAsInt());
    }

    @Test public void playTriggersReachRollAndLyingCrawl() throws Exception {
        assertEquals(13, run("roll", "--data=playRoll=1", "--advance=60").get("state").getAsInt());
        assertEquals(11, run("liecrawl", "--data=playLieCrawl=1", "--advance=120").get("state").getAsInt());
    }

    @Test public void partInteractionsReachDistinctStatesAndResetInactivity() throws Exception {
        Map<String, JsonElement> head = run("pet-head", "--advance=60s", "--data=touchActivity=1", "--data=petHead=1", "--advance=30");
        assertEquals(17, head.get("state").getAsInt());
        assertTrue(head.get("inactive").getAsDouble() < 1);
        assertEquals(18, run("nose", "--data=boopNose=1", "--advance=24").get("state").getAsInt());
        assertEquals(20, run("shy", "--data=petHead=1", "--advance=24", "--data=teaseHead=1", "--advance=12").get("state").getAsInt());
        assertEquals(0, run("pet-ended", "--data=petHead=1", "--advance=92").get("state").getAsInt());
    }

    @Test public void bellyTickleNeedsTheBellyUpPartOfRollAndEndsNormally() throws Exception {
        assertEquals(0, run("belly-guard", "--data=tickleBelly=1", "--advance=12").get("state").getAsInt());
        assertEquals(19, run("belly-active", "--data=playRoll=1", "--advance=120", "--data=tickleBelly=1", "--advance=12").get("state").getAsInt());
        assertEquals(0, run("belly-ended", "--data=playRoll=1", "--advance=120", "--data=tickleBelly=1", "--advance=146").get("state").getAsInt());
    }

    @Test public void edgeRunTucksAndKeepsPeekingUntilPortraitReturns() throws Exception {
        assertEquals(21, run("edge-run", "--advance=98s", "--data=edgeMode=true", "--advance=30").get("state").getAsInt());
        assertEquals(22, run("edge-tuck", "--data=edgeMode=true", "--advance=30", "--data=edgePeek=true", "--advance=12").get("state").getAsInt());
        assertEquals(23, run("edge-hold", "--data=edgeMode=true", "--advance=30", "--data=edgePeek=true", "--advance=120s").get("state").getAsInt());
        assertEquals(24, run("edge-pet", "--data=edgeMode=true", "--advance=30", "--data=edgePeek=true", "--advance=30", "--data=petPeek=1", "--advance=24").get("state").getAsInt());
        Map<String, JsonElement> restored = run("edge-exit", "--data=edgeMode=true", "--advance=30", "--data=edgePeek=true", "--advance=120s", "--data=touchActivity=1", "--data=edgeMode=false", "--advance=3");
        assertEquals(0, restored.get("state").getAsInt());
        assertTrue(restored.get("inactive").getAsDouble() < 1);
    }

    @Test public void signInterruptsSleepAndEdgeThenHoldsUntilDismissed() throws Exception {
        assertEquals(25, run("raise-sign", "--advance=100s", "--data=holdSign=true", "--advance=12").get("state").getAsInt());
        assertEquals(26, run("hold-sign", "--data=holdSign=true", "--advance=120s", "--data=playRoll=1", "--data=petHead=1", "--advance=30").get("state").getAsInt());
        assertEquals(26, run("edge-sign", "--data=edgeMode=true", "--data=edgePeek=true", "--advance=120", "--data=holdSign=true", "--advance=30").get("state").getAsInt());
        assertEquals(0, run("lower-sign", "--data=holdSign=true", "--advance=120s", "--data=touchActivity=1", "--data=holdSign=false", "--advance=3").get("state").getAsInt());
        assertEquals(21, run("sign-resume-edge", "--data=edgeMode=true", "--data=holdSign=true", "--advance=60", "--data=holdSign=false", "--advance=3").get("state").getAsInt());
    }

    @Test public void selectionDoesNotRepeatBeforeBagIsEmpty() throws Exception {
        Map<String, JsonElement> first = run("first", "--advance=120");
        int selected = first.get("state").getAsInt();
        assertTrue(selected >= 1 && selected <= 7);
        List<String> steps = new ArrayList<>();
        // 已做过的动作不再进入候选，直到七个标记全部清空。
        for (String action : ACTIONS) steps.add("--data=seen" + action + "=true");
        steps.add("--data=seenLook=false");
        steps.add("--advance=120");
        assertEquals(6, run("only-look", steps.toArray(new String[0])).get("state").getAsInt());
        Map<String, JsonElement> reset = run("bag-reset", "--data=seenBlink=true", "--data=seenLick=true",
                "--data=seenTail=true", "--data=seenStretch=true", "--data=seenYawn=true",
                "--data=seenLook=true", "--data=seenCrawl=true", "--advance=120");
        int seen = 0;
        for (String action : ACTIONS) if (reset.get("seen" + action).getAsBoolean()) seen++;
        assertEquals(1, seen);
    }
}
