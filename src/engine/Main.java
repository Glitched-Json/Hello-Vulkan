package engine;

import lombok.Getter;
import org.lwjgl.glfw.GLFWErrorCallback;

import java.text.DecimalFormat;
import java.util.Objects;

import static org.lwjgl.glfw.GLFW.*;

public final class Main {
    @Getter private static boolean running = true;
    private static final DecimalFormat format = new DecimalFormat(",###");

    public static void main() {
        initialize();
        run();
        cleanup();
    }

    private static void spatialRender() {

    }

    private static void render(double t) {
        Window.frameUpdate();

        InputManager.update();
        glfwPollEvents();
    }

    private static void update(double t) {

    }

    private static void run() {
        double targetFPS = 1d / Math.max(1e-9, DataManager.getSetting("fps")); // frames per second
        double targetTPS = 1d / DataManager.getSetting("tps");                 // ticks  per second
        double targetSRR = 1d / DataManager.getSetting("spatial_fps");         // spatial refresh rate
        boolean uncappedFPS = DataManager.getFlag("uncapped_FPS");
        boolean spacialMatchFPS = DataManager.getFlag("spatial_match_fps");

        double tFPS = 0, elapsedTime;
        long start, end = System.nanoTime(), passedTime;
        double timeRender = 0, timeStatic = 0, timeSpatial = 0;
        int fpsCounter = 0, tpsCounter = 0, srrCounter = 0;

        boolean showMetrics = DataManager.getFlag("show_metrics_on_window_title");

        while (Window.shouldNotClose()) {
            // Timer Updating
            start = System.nanoTime();
            passedTime = start - end;
            end = start;
            elapsedTime = passedTime / 1e9d;
            timeRender += elapsedTime;
            timeStatic += elapsedTime;
            timeSpatial += elapsedTime;
            tFPS += elapsedTime;

            // Spatial Rendering
            if (!spacialMatchFPS && timeSpatial >= targetSRR) {
                spatialRender();
                timeSpatial %= targetSRR;
                srrCounter++;
            }

            // Rendering
            if (Window.isVSync() || uncappedFPS) {
                if (spacialMatchFPS) {
                    spatialRender();
                    srrCounter++;
                }
                render(timeRender);
                timeRender = 0;
                fpsCounter++;
            } else if (timeRender >= targetFPS) {
                if (spacialMatchFPS) {
                    spatialRender();
                    srrCounter++;
                }
                render(timeRender);
                timeRender %= targetFPS;
                fpsCounter++;
            }

            // Static Update
            if (timeStatic >= targetTPS) {
                update(targetTPS);
                timeStatic %= targetTPS;
                tpsCounter++;
            }

            // FPS
            if (showMetrics && tFPS >= 1.) {
                tFPS %= 1.;
                Window.setTitle("FPS: %s | TPS: %s | SRR: %s".formatted(
                        format.format(fpsCounter).replaceAll(",", "."),
                        format.format(tpsCounter).replaceAll(",", "."),
                        format.format(srrCounter).replaceAll(",", ".")
                ));
                fpsCounter = tpsCounter = srrCounter = 0;
            }
        }
    }

    private static void initialize() {
        DataManager.infoMessage("show_info_os_arch", System.getProperty("os.arch"));

        long start = System.nanoTime();

        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Failed to initialize GLFW.");
        DataManager.initializeMessage("GLFW");

        Window.initialize();
        VulkanManager.initialize();

        long end = System.nanoTime();
        if (DataManager.getInfoFlag("show_initialization_metrics"))
            System.out.printf("[INITIALIZATION COMPLETE]: %sms | %sns\n",
                    format.format((end - start) / 1e9d).replaceAll(",", "."),
                    format.format(end - start).replaceAll(",", ".")
            );
    }

    private static void cleanup() {
        running = false;
        long start = System.nanoTime();

        Window.cleanup();
        VulkanManager.cleanup();

        glfwTerminate();
        Objects.requireNonNull(glfwSetErrorCallback(null)).free();
        DataManager.cleanupMessage("GLFW");

        long end = System.nanoTime();
        if (DataManager.getInfoFlag("show_cleanup_metrics"))
            System.out.printf("[CLEANUP COMPLETE]: %sms | %sns\n",
                    format.format((end - start) / 1e9d).replaceAll(",", "."),
                    format.format(end - start).replaceAll(",", ".")
            );
    }
}