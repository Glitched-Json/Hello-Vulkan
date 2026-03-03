package engine;

import lombok.Getter;
import org.joml.*;
import org.lwjgl.glfw.GLFWVidMode;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;

@SuppressWarnings("unused")
public final class Window {
    private static boolean initialized = false;
    @Getter private static boolean vSync = true;
    private static long id;
    @Getter private static int width = 1280, height = 720;
    @Getter private static String title = "Window";

    private Window() {}

    @SuppressWarnings("resource")
    public static void initialize() {
        if (initialized) return;
        initialized = true;

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        if (DataManager.getFlag("anti_aliasing_samples")) glfwWindowHint(GLFW_SAMPLES, (int) DataManager.getSetting("anti_aliasing_samples"));
        //noinspection AssignmentUsedAsCondition
        glfwWindowHint(GLFW_DOUBLEBUFFER, (vSync = DataManager.getFlag("vSync")) ? GLFW_TRUE : GLFW_FALSE);

        GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        assert mode != null;

        if ((id = glfwCreateWindow(width, height, "Title", 0, 0)) == 0)
            throw new RuntimeException("Failed to initialize the GLFW Window.");
        glfwSetWindowPos(id, (mode.width() - width) / 2, (mode.height() - height) / 2);
        setTitle(title);

        glfwSetKeyCallback(id, (id, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(id, true);

            InputManager.registerKeyInput(new Vector4i(key, scancode, action, mods));
        });

        glfwSetMouseButtonCallback(id, (_, button, action, mods) ->
                InputManager.registerMouseInput(new Vector3i(button, action, mods)));

        glfwSetFramebufferSizeCallback(id, (_, width, height) -> {
            Window.width = width;
            Window.height = height;
            setViewport();
            // SpatialManager.resize();
        });

        glfwSetCursorPosCallback(id, (_, xPosition, yPosition) ->
                InputManager.registerCursorPosition(new Vector2d(xPosition, yPosition)));

        glfwMakeContextCurrent(id);
        if (vSync) glfwSwapInterval(DataManager.getFlag("uncapped_FPS") ? 0 : 1);
        lockCursor(DataManager.getFlag("first_person_mode"));
        glfwShowWindow(id);

        if (DataManager.getFlag("show_initialization_messages"))
            System.out.println("Window Initialized");
    }

    public static void lockCursor(boolean lock) {glfwSetInputMode(id, GLFW_CURSOR, lock ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);}

    public static void setViewport() {
        // TODO
    }
    public static void setTitle(String title) { glfwSetWindowTitle(id, Window.title = title); }

    public static float getAspectRatio() { return width / (float) height; }

    public static String getClipboard() { return glfwGetClipboardString(id); }

    public static void setClipboard(String clipboard) { glfwSetClipboardString(id, clipboard); }

    public static float getOpacity() { return glfwGetWindowOpacity(id); }

    public static void setOpacity(Number opacity) { glfwSetWindowOpacity(id, opacity.floatValue()); }

    public static Vector2f getContentScale() {float[][] s = new float[2][1]; glfwGetWindowContentScale(id, s[0], s[1]); return new Vector2f(s[0][0], s[1][0]);}
    public static Vector4i getFrameSize() {int[][] f = new int[4][1]; glfwGetWindowFrameSize(id, f[0], f[1], f[2], f[3]); return new Vector4i(f[0][0], f[1][0], f[2][0], f[3][0]);}
    public static Vector2i getPosition() {int[][] p = new int[2][1]; glfwGetWindowPos(id, p[0], p[1]); return new Vector2i(p[0][0], p[1][0]);}
    public static Vector2i getSize() {int[][] s = new int[2][1]; glfwGetWindowSize(id, s[0], s[1]); return new Vector2i(s[0][0], s[1][0]);}

    public static boolean shouldNotClose() { return !glfwWindowShouldClose(id); }
    public static void swapBuffers() { glfwSwapBuffers(id); }
    public static void focus() { glfwFocusWindow(id); }
    public static void show() { glfwShowWindow(id); }
    public static void hide() { glfwHideWindow(id); }
    public static void iconify() { glfwIconifyWindow(id); }
    public static void maximize() { glfwMaximizeWindow(id); }
    public static void requestAttention() { glfwRequestWindowAttention(id); }

    public static void frameUpdate() {
        // TODO
    }

    public static void cleanup() {
        if (!initialized || Main.isRunning()) return;
        initialized = false;

        glfwFreeCallbacks(id);
        glfwDestroyWindow(id);
    }
    //*/
}
