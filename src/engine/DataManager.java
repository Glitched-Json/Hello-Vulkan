package engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.vulkan.VK10.VK_MAKE_VERSION;

@SuppressWarnings("unused")
public final class DataManager {
    private static final Map<String, Float> settings = new HashMap<>();
    private static final Map<String, List<String>> stringSettings = new HashMap<>();
    static {
        try {
            // Pattern pattern = Pattern.compile("^\\s*([a-z][\\w ]*?)\\s*[:=]\\s*((\\d+(?:\\.\\d*)?)|(true|false))", Pattern.CASE_INSENSITIVE);
            Pattern pattern = Pattern.compile("^\\s*([a-z][\\w ]*?)\\s*[:=]\\s*(.*)", Pattern.CASE_INSENSITIVE);
            Pattern comments = Pattern.compile("^(.*?)(?:(//|/\\*).*)?$", Pattern.MULTILINE);
            Pattern multilineBreak = Pattern.compile(".*\\*/(.*)", Pattern.MULTILINE);
            String line;
            BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("settings.info"))));
            Matcher commentMatcher;
            boolean multilineComment = false;
            float value;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                if (multilineComment) {
                    Matcher multiMatcher = multilineBreak.matcher(line);
                    if (multiMatcher.find()) {
                        line = multiMatcher.group(1);
                        multilineComment = false;
                    } else continue;
                }

                commentMatcher = comments.matcher(line);
                if (commentMatcher.find()) {
                    line = commentMatcher.group(1);
                    if (commentMatcher.group(2) != null && commentMatcher.group(2).equals("/*")) multilineComment = true;
                }

                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    List<String> stringValues = new ArrayList<>();
                    try {value = switch (matcher.group(2).trim().toLowerCase()) {
                        case "true" -> 1;
                        case "false" -> 0;
                        default -> Float.parseFloat(matcher.group(2).trim());
                    };} catch (NullPointerException | NumberFormatException ignored) {
                        value = 0;
                        stringValues.addAll(Arrays.stream(matcher.group(2).trim().split(",")).map(String::trim).toList());
                    }
                    settings.put(matcher.group(1), value);
                    stringSettings.put(matcher.group(1), stringValues);
                }
            }
        } catch (IOException ignored) {}
    }

    private DataManager() {}

    public static boolean resourceExists(String resource) {
        return ClassLoader.getSystemResource(resource) != null;
    }

    public static String readResource(String resource) {
        try {
            StringBuilder result = new StringBuilder();
            String line;

            BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream(resource))));
            while ((line = reader.readLine()) != null) result.append(line).append("\n");
            return result.toString();
        } catch (NullPointerException | IOException ignored) {
            return "";
        }
    }

    public static List<File> getAllDirectoryResourceFiles(String path) {
        path += "/";
        List<File> list = new ArrayList<>();
        try {
            ClassLoader.getSystemResourceAsStream(path);
            File[] listOfFiles = new File(ClassLoader.getSystemResource(path).toURI()).listFiles();
            assert listOfFiles != null;
            for (File file : listOfFiles) {
                if (file.isFile()) list.add(file);
                else if (file.isDirectory()) list.addAll(getAllDirectoryResourceFiles(path + file.getName()));
            }
        } catch (URISyntaxException e) {throw new RuntimeException(e);}
        return list;
    }

    public static float getSetting(String var) {
        if (settings.containsKey(var)) return settings.get(var);
        return 0;
    }

    public static float getSettingClamped(String var, Number min, Number max) {
        return Logic.clamp(getSetting(var), min, max);
    }

    public static boolean getFlag(String var) {
        if (settings.containsKey(var)) return settings.get(var) != 0;
        return false;
    }

    public static List<String> getSettingList(String var) {
        if (stringSettings.containsKey(var)) return new ArrayList<>(stringSettings.get(var));
        return new ArrayList<>();
    }

    public static String getSettingString(String var) {
        if (stringSettings.containsKey(var)) return stringSettings.get(var).getFirst();
        return "";
    }

    public static void initializeMessage(String message) {
        if (getFlag("show_initialization_messages"))
            System.out.println("[INITIALIZED]: " + message);
    }

    public static void cleanupMessage(String message) {
        if (getFlag("show_cleanup_messages"))
            System.out.println("[CLEANUP]: " + message);
    }

    public static int getVulkanVersion(String setting) {
        List<String> numbers = getSettingList(setting);
        int[] n = new int[]{0, 0, 0};
        for (int i=0; i<3 && i<numbers.size(); i++) try {
            n[i] = (int) Double.parseDouble(numbers.get(i));
        } catch (NumberFormatException e) { n[i] = 0; }
        return VK_MAKE_VERSION(n[0], n[1], n[2]);
    }
}
