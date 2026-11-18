package com.yuyu.bongocat;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** 用原生截图检查藏身和中断时，头身是否出现分离的大块轮廓。 */
public class RiveContinuityTest {
    @Test public void headAndBodyStayConnectedThroughEveryTuckFrame() throws Exception {
        File root = new File(System.getProperty("bongocat.projectDir", "."));
        String configured = System.getenv("RIVE_CLI");
        File cli = configured == null ? new File(System.getProperty("user.home"),
                ".rive/bin/" + (System.getProperty("os.name").contains("Windows") ? "rive.exe" : "rive")) : new File(configured);
        assumeTrue("需要 Rive CLI 渲染真实画面", cli.isFile());
        File directory = new File(root, "build/rive-continuity");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        List<String> commands = new ArrayList<>();
        List<File> pictures = new ArrayList<>();
        for (String scenario : new String[]{"white", "dark-scarf", "interrupted", "restarted"}) {
            commands.add("reload");
            if (scenario.equals("dark-scarf")) {
                commands.add("data bodyColor=FF707070"); commands.add("data costume=5");
            }
            commands.add("data edgeMode=true"); commands.add("advance 60");
            commands.add("data edgePeek=true");
            boolean interrupted = scenario.equals("interrupted") || scenario.equals("restarted");
            if (interrupted) {
                commands.add("advance 9");
                commands.add(scenario.equals("interrupted") ? "data edgeMode=false" : "data edgePeek=false");
            }
            for (int frame = 0; frame <= (interrupted ? 8 : 28); frame++) {
                if (frame > 0) commands.add("advance 1");
                File file = new File(directory, scenario + "-" + frame + ".png");
                pictures.add(file);
                commands.add("screenshot " + file.getAbsolutePath().replace('\\', '/'));
            }
        }
        commands.add("quit");
        File script = new File(directory, "commands.txt"), log = new File(directory, "capture.log");
        Files.write(script.toPath(), commands, StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(cli.getAbsolutePath(), "rive", "--commands=" + script.getAbsolutePath())
                .directory(root).redirectErrorStream(true).redirectOutput(log).start();
        if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("连续性截图超时"); }
        String output = Files.readString(log.toPath());
        assertEquals(output, 0, process.exitValue());
        assertFalse(output, output.contains("\nerr "));
        for (File picture : pictures) {
            int[] result = foreground(ImageIO.read(picture));
            assertEquals("头身断开：" + picture.getName(), 1, result[0]);
            if (picture.getName().equals("white-0.png")) assertTrue("起点应显示全身", result[1] < 300);
            if (picture.getName().equals("white-28.png")) assertTrue("终点只露出脑袋", result[1] >= 320);
        }
    }

    private int[] foreground(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight(), background = image.getRGB(0, 0);
        boolean[] mask = new boolean[width * height];
        int[] queue = new int[mask.length];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int rgb = image.getRGB(x, y), difference = 0;
            for (int shift : new int[]{0, 8, 16}) difference = Math.max(difference,
                    Math.abs(((rgb >> shift) & 255) - ((background >> shift) & 255)));
            mask[y * width + x] = difference > 16;
        }
        int largeParts = 0, top = height;
        for (int first = 0; first < mask.length; first++) {
            if (!mask[first]) continue;
            int read = 0, write = 1, minY = height;
            queue[0] = first; mask[first] = false;
            while (read < write) {
                int current = queue[read++], x = current % width, y = current / width;
                minY = Math.min(minY, y);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    int next = ny * width + nx;
                    if (mask[next]) { mask[next] = false; queue[write++] = next; }
                }
            }
            // 忽略独立的抗锯齿碎点，只统计足以形成身体部件的区域。
            if (write >= 250) { largeParts++; top = Math.min(top, minY); }
        }
        return new int[]{largeParts, top};
    }
}
